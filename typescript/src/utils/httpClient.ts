import axios from 'axios';
import logger from './logger';
import type { AxiosInstance } from 'axios';

export class HttpClient {
  private static instance: AxiosInstance;
  private static baseURL: string;

  private static getInstance(): AxiosInstance {
    if (!this.instance) {
      this.instance = axios.create({
        baseURL: this.baseURL || 'http://localhost:3000',
        timeout: 30000,
        headers: {
          'Content-Type': 'application/json'
        }
      });

      // Add response interceptor for logging
      this.instance.interceptors.response.use(
        (response) => {
          logger.info(`API Response [${response.config.method}] ${response.config.url}:`, {
            status: response.status,
            data: response.data
          });
          return response;
        },
        (error) => {
          logger.error(`API Error [${error.config?.method}] ${error.config?.url}:`, {
            status: error.response?.status,
            error: error.response?.data || error.message
          });
          return Promise.reject(error);
        }
      );
    }
    return this.instance;
  }

  public static setBaseURL(url: string): void {
    this.baseURL = url;
    this.instance = this.getInstance(); // Create new instance with new baseURL
  }

  public static async get<T>(url: string): Promise<T> {
    const response = await this.getInstance().get<T>(url);
    return response.data;
  }

  public static async post<T>(url: string, data: any): Promise<T> {
    const response = await this.getInstance().post<T>(url, data);
    return response.data;
  }
} 
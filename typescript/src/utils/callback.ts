import logger from './logger';
import axios from 'axios';

export interface UpdateCallback {
    (message: string): Promise<void>;
}

export const createTelegramCallback = (chatId: number): UpdateCallback => {
    return async (message: string) => {
        try {
            await axios.post('http://localhost:8888/api/telegram/send', {
                chatId,
                text: message
            });
        } catch (error) {
            logger.error('Failed to send update message:', error);
        }
    };
};

export const createConsoleCallback = (): UpdateCallback => {
    return async (message: string) => {
        logger.info(`Update: ${message}`);
    };
}; 
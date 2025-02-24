import axios, { type AxiosInstance } from 'axios';
import logger from './logger';

/**
 * Type for signature status
 */
interface SignatureStatus {
    slot: number;
    confirmations: number | null;
    err: any | null;
    confirmationStatus?: 'processed' | 'confirmed' | 'finalized';
    status?: {
        Ok: null;
    };
}

/**
 * Type for signature status response
 */
interface SignatureStatusResponse {
    context: {
        slot: number;
    };
    value: (SignatureStatus | null)[];
}

export class HeliusRpcInterface {
    private readonly rpcClient: AxiosInstance;
    private readonly apiKey: string;
    private readonly baseUrl: string;

    constructor(apiKey: string, baseUrl: string) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.rpcClient = axios.create({
            baseURL: `${baseUrl}?api-key=${this.apiKey}`,
            headers: {
                'Content-Type': 'application/json',
            },
            timeout: 30000, // 30 seconds
        });
    }

    /**
     * Generates a JSON-RPC request object
     * @param method The RPC method name
     * @param params Optional parameters for the method
     * @returns JSON-RPC request object
     */
    private generateRequest(method: string, params?: any[]): object {
        return {
            jsonrpc: '2.0',
            id: Date.now(),
            method,
            params: params || [],
        };
    }

    /**
     * Makes an RPC call to Helius
     * @param method The RPC method name
     * @param params Optional parameters for the method
     * @returns Promise resolving to the RPC response
     */
    public async callRpcMethod<T>(method: string, params?: any[]): Promise<T> {
        try {
            const request = this.generateRequest(method, params);
            logger.debug(`Making Helius RPC call: ${method}`, { params });

            const response = await this.rpcClient.post('', request);
            
            if (response.data.error) {
                throw new Error(`Helius RPC error: ${JSON.stringify(response.data.error)}`);
            }

            return response.data.result;
        } catch (error) {
            logger.error('Helius RPC call failed:', error);
            throw new Error(`Helius RPC call failed: ${error instanceof Error ? error.message : String(error)}`);
        }
    }

    /**
     * Gets enhanced transaction details
     * @param signature Transaction signature
     */
    public async getEnhancedTransaction(signature: string) {
        return this.callRpcMethod('getTransaction', [
            signature,
            { encoding: 'jsonParsed', maxSupportedTransactionVersion: 0 }
        ]);
    }

    /**
     * Gets parsed NFT events
     * @param signatures Array of transaction signatures
     */
    public async getParsedNftEvents(signatures: string[]) {
        return this.callRpcMethod('getParsedNftEvents', [signatures]);
    }

    /**
     * Gets NFT events by owner
     * @param ownerAddress Owner's wallet address
     * @param limit Maximum number of events to return
     */
    public async getNftEventsByOwner(ownerAddress: string, limit: number = 100) {
        return this.callRpcMethod('getNftEventsByOwner', [ownerAddress, { limit }]);
    }

    /**
     * Gets active listings for an NFT
     * @param mint NFT mint address
     */
    public async getActiveListings(mint: string) {
        return this.callRpcMethod('getActiveListings', [mint]);
    }

    /**
     * Gets all NFTs owned by an address
     * @param ownerAddress Owner's wallet address
     * @param page Page number for pagination
     * @param limit Items per page
     */
    public async getNftsByOwner(ownerAddress: string, page: number = 1, limit: number = 100) {
        return this.callRpcMethod('getNftsByOwner', [
            ownerAddress,
            { page, limit }
        ]);
    }

    /**
     * Gets token balances for an address
     * @param ownerAddress Owner's wallet address
     */
    public async getTokenBalances(ownerAddress: string) {
        return this.callRpcMethod('getTokenBalances', [ownerAddress]);
    }

    /**
     * Gets mint list details
     * @param mints Array of mint addresses
     */
    public async getMintList(mints: string[]) {
        return this.callRpcMethod('getMintList', [{ mints }]);
    }

    /**
     * Gets DAS API response
     * @param mintAccounts Array of mint accounts
     */
    public async getDasApiAsset(mintAccounts: string[]) {
        return this.callRpcMethod('getDasApiAsset', [{ mintAccounts }]);
    }

    /**
     * Searches for NFTs by criteria
     * @param searchCriteria Search parameters
     */
    public async searchAssets(searchCriteria: {
        ownerAddress?: string,
        collectionAddress?: string,
        creatorAddress?: string,
    }) {
        return this.callRpcMethod('searchAssets', [searchCriteria]);
    }

    /**
     * Gets the status of a signature or multiple signatures
     * @param signatures Array of transaction signatures
     * @param searchTransactionHistory Whether to search transaction history
     */
    public async getSignatureStatuses(
        signatures: string[],
        searchTransactionHistory: boolean = true
    ): Promise<SignatureStatusResponse> {
        return this.callRpcMethod('getSignatureStatuses', [
            signatures,
            { searchTransactionHistory }
        ]);
    }

    /**
     * Gets all tokens owned by an address with pagination
     * @param ownerAddress Owner's wallet address
     * @param page Page number
     * @param limit Items per page
     */
    public async getAllTokensByOwner(
        ownerAddress: string,
        page: number = 1,
        limit: number = 1000
    ) {
        return this.callRpcMethod('getTokenAccounts', [
            ownerAddress,
            { page, limit }
        ]);
    }

    /**
     * Gets account info
     * @param pubkey Public key of the account
     * @param encoding Response encoding format
     */
    public async getAccountInfo(
        pubkey: string,
        encoding: 'base58' | 'base64' | 'jsonParsed' = 'jsonParsed'
    ) {
        return this.callRpcMethod('getAccountInfo', [
            pubkey,
            { encoding }
        ]);
    }

    /**
     * Gets multiple accounts info
     * @param pubkeys Array of public keys
     * @param encoding Response encoding format
     */
    public async getMultipleAccounts(
        pubkeys: string[],
        encoding: 'base58' | 'base64' | 'jsonParsed' = 'jsonParsed'
    ) {
        return this.callRpcMethod('getMultipleAccounts', [
            pubkeys,
            { encoding }
        ]);
    }

    /**
     * Gets program accounts
     * @param programId Program ID
     * @param encoding Response encoding format
     */
    public async getProgramAccounts(
        programId: string,
        encoding: 'base58' | 'base64' | 'jsonParsed' = 'jsonParsed'
    ) {
        return this.callRpcMethod('getProgramAccounts', [
            programId,
            { encoding }
        ]);
    }

    /**
     * Gets latest block height
     */
    public async getBlockHeight(): Promise<number> {
        return this.callRpcMethod('getBlockHeight', []);
    }

    /**
     * Gets block production information
     */
    public async getBlockProduction() {
        return this.callRpcMethod('getBlockProduction', []);
    }

    /**
     * Gets block commitment
     */
    public async getBlockCommitment(block: number) {
        return this.callRpcMethod('getBlockCommitment', [block]);
    }

    /**
     * Gets recent performance samples
     */
    public async getRecentPerformanceSamples() {
        return this.callRpcMethod('getRecentPerformanceSamples', []);
    }

    async getAssetsByGroup(params: { groupKey: string; groupValue: string }): Promise<any> {
        try {
            const response = await axios.post(
                `${this.baseUrl}?api-key=${this.apiKey}`,
                {
                    jsonrpc: '2.0',
                    id: 'helius-test',
                    method: 'getAssetsByGroup',
                    params
                }
            );
            return response.data.result;
        } catch (error) {
            logger.error('Error fetching assets by group:', error);
            throw error;
        }
    }
}

// Example usage:
/*
const helius = new HeliusRpcInterface('your-api-key-here');

// Get enhanced transaction details
const txDetails = await helius.getEnhancedTransaction('transaction-signature');

// Get NFTs owned by an address
const nfts = await helius.getNftsByOwner('wallet-address');

// Get token balances
const balances = await helius.getTokenBalances('wallet-address');
*/ 
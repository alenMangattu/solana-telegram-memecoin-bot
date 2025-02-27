import { PublicKey } from '@solana/web3.js';

export interface WalletInfo {
  publicKey: string;
  privateKey: string;
}

export interface MemecoinConfig {
  name: string;
  symbol: string;
  decimals: number;
  supply: number;
  metadataUri: string;
  revokeMintAuthority: boolean;
  revokeFreezeAuthority: boolean;
}

export interface TokenMetadata {
  name: string;
  symbol: string;
  image?: string;
  description?: string;
  [key: string]: any;
}

export interface TokenInfo {
  address: string;
  mintAuthority: string;
  freezeAuthority: string | null;
  decimals: number;
  supply: string;
  metadata?: {
    name: string;
    symbol: string;
    uri: string;
    sellerFeeBasisPoints: number;
  };
  raydiumInfo?: {
    poolAddress: string;
    liquidity: string;
    priceInSol: number;
  };
  holderCount?: number;
  isInitialized: boolean;
  lastUpdated: string;
} 
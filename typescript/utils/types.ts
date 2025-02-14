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
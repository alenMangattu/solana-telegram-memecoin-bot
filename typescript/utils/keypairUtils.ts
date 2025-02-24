import bs58 from 'bs58';
import fs from 'fs';
import path from 'path';
import os from 'os';
import { v4 as uuidv4 } from 'uuid';
import logger from './logger';

export class KeypairUtils {
    static async saveWalletKeypair(privateKey: string): Promise<string> {
        try {
            const tempDir = path.join(os.homedir(), '.config', 'solana', 'temp');
            await fs.promises.mkdir(tempDir, { recursive: true });

            const filename = `${uuidv4()}.json`;
            const keypairPath = path.join(tempDir, filename);

            const secretKey = bs58.decode(privateKey);

            if (secretKey.length !== 64) {
                throw new Error(`Invalid secret key length: ${secretKey.length} (expected 64)`);
            }

            const jsonArray = Array.from(secretKey);
            const jsonContent = JSON.stringify(jsonArray);

            await fs.promises.writeFile(keypairPath, jsonContent);
            await fs.promises.chmod(keypairPath, 0o600);

            logger.info(`Created keypair file at: ${keypairPath}`);
            return keypairPath;
        } catch (error) {
            logger.error('Error saving wallet keypair:', error);
            throw new Error('Failed to save wallet keypair');
        }
    }

    static async cleanupKeypairFile(keypairPath: string): Promise<void> {
        try {
            await fs.promises.unlink(keypairPath);
            logger.info(`Cleaned up keypair file: ${keypairPath}`);
        } catch (error) {
            logger.error('Error cleaning up keypair file:', error);
            // Don't throw here as this is cleanup code
        }
    }
}
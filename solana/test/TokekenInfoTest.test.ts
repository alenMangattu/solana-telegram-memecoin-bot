import { TokenInfoService } from '../src/utils/tokeninfo';
import { Connection } from '@solana/web3.js';
import dotenv from 'dotenv';

dotenv.config();

describe('TokenInfoService', () => {
    let tokenInfoService: TokenInfoService;

    let tokenAddress= "6pKHwNCpzgZuC9o5FzvCZkYSUGfQddhUYtMyDbEVpump";
    beforeAll(() => {
        tokenInfoService = new TokenInfoService();
    });

    test('should fetch token information', async () => {
        const tokenInfo = await tokenInfoService.getBasicTokenInfo(tokenAddress);

        expect(tokenInfo).toHaveProperty('address', tokenAddress);
        expect(tokenInfo).toHaveProperty('name');
        expect(tokenInfo).toHaveProperty('symbol');
        expect(tokenInfo).toHaveProperty('decimals');
        expect(tokenInfo).toHaveProperty('totalSupply');
    });

    test('should throw an error for invalid token address', async () => {
        const invalidTokenAddress = 'invalid-token-address';

        await expect(tokenInfoService.getTokenInformation(invalidTokenAddress)).rejects.toThrow();
    });

    test('should fetch basic token info', async () => {
        const basicInfo = await tokenInfoService.getBasicTokenInfo(tokenAddress); // Changed to getTokenInformation

        expect(basicInfo).toHaveProperty('name');
        expect(basicInfo).toHaveProperty('symbol');
        expect(basicInfo).toHaveProperty('decimals');
        expect(basicInfo).toHaveProperty('totalSupply');
    });

    // Add more tests as needed for other methods
});

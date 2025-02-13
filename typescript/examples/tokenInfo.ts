import { TokenInfoService } from '../utils/tokenInfo';

async function main() {
    const tokenInfoService = new TokenInfoService();
    
    // Example token address (e.g., USDC on Solana)
    const tokenAddress = 'EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v';
    
    try {
        const tokenInfo = await tokenInfoService.getTokenInformation(tokenAddress);
        console.log('Token Information:', JSON.stringify(tokenInfo, null, 2));
    } catch (error) {
        console.error('Error:', error);
    }
}

main(); 
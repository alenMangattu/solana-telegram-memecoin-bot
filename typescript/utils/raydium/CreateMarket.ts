import { OPEN_BOOK_PROGRAM, WSOLMint } from '@raydium-io/raydium-sdk-v2'
import { initSdk, txVersion } from '../../config'
import { PublicKey, Keypair } from '@solana/web3.js'
import bs58 from 'bs58'

export const createMarket = async (
  tokenMint: string,
  walletPrivateKey: string,
  lotSize: number = 1,
  tickSize: number = 0.01
) => {
  const raydium = await initSdk()

  // Decode private key
  const decodedPrivateKey = bs58.decode(walletPrivateKey)
  const walletKeypair = Keypair.fromSecretKey(decodedPrivateKey)

  // Create the market
  const { execute, extInfo } = await raydium.marketV2.create({
    baseInfo: {
      mint: new PublicKey(tokenMint),
      decimals: 9,
    },
    quoteInfo: {
      mint: WSOLMint,
      decimals: 9,
    },
    lotSize,
    tickSize,
    dexProgramId: OPEN_BOOK_PROGRAM,
    txVersion,
  })

  // Execute market creation
  const txIds = await execute({ sequentially: true })
  
  // Return market ID for future reference
  return extInfo.address.marketId.toBase58()
}

/** uncomment code below to execute */
// createMarket()
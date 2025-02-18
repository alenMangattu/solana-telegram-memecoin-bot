import { Raydium, TxVersion, parseTokenAccountResp, type Cluster } from '@raydium-io/raydium-sdk-v2'
import { Connection, Keypair } from '@solana/web3.js'
import { TOKEN_PROGRAM_ID, TOKEN_2022_PROGRAM_ID } from '@solana/spl-token'
import dotenv from 'dotenv'
import bs58 from 'bs58'

dotenv.config()

// Validate environment variables
if (!process.env.RPC_URL) throw new Error('RPC_URL is required in environment')
if (!process.env.SOLANA_CLUSTER) throw new Error('SOLANA_CLUSTER is required in environment')

// Connection setup
export const connection = new Connection(process.env.RPC_URL, 'confirmed')
export const txVersion = TxVersion.V0

// Validate and set cluster
const validClusters = ['mainnet', 'devnet'] as const
const cluster = process.env.SOLANA_CLUSTER as Cluster
if (!validClusters.includes(cluster)) {
    throw new Error('SOLANA_CLUSTER must be either "mainnet" or "devnet"')
}

// Initialize Raydium SDK
let raydium: Raydium | undefined

export const initSdk = async (walletPrivateKey?: string) => {
    if (!raydium || walletPrivateKey) {
        // If walletPrivateKey is provided, create a new instance with that key
        const owner = walletPrivateKey 
            ? Keypair.fromSecretKey(bs58.decode(walletPrivateKey))
            : Keypair.generate() // Generate temporary keypair if no key provided

        raydium = await Raydium.load({
            connection,
            owner,
            cluster,
            disableFeatureCheck: true,
            blockhashCommitment: 'confirmed',
        })

        // Set up token account handling
        const tokenAccountData = await fetchTokenAccountData(owner)
        raydium.account.updateTokenAccount(tokenAccountData)

        // Update token accounts when balance changes
        connection.onAccountChange(owner.publicKey, async () => {
            const updatedTokenData = await fetchTokenAccountData(owner)
            raydium!.account.updateTokenAccount(updatedTokenData)
        })
    }

    return raydium
}

// Helper function to fetch token account data
async function fetchTokenAccountData(owner: Keypair) {
    const solAccountResp = await connection.getAccountInfo(owner.publicKey)
    const tokenAccountResp = await connection.getTokenAccountsByOwner(owner.publicKey, {
        programId: TOKEN_PROGRAM_ID,
    })
    const token2022Resp = await connection.getTokenAccountsByOwner(owner.publicKey, {
        programId: TOKEN_2022_PROGRAM_ID,
    })

    return parseTokenAccountResp({
        owner: owner.publicKey,
        solAccountResp,
        tokenAccountResp: {
            context: tokenAccountResp.context,
            value: [...tokenAccountResp.value, ...token2022Resp.value],
        },
    })
}

// Export common constants
export const COMMITMENT = 'confirmed'
export const DEFAULT_SLIPPAGE = 0.01 // 1%

// export const grpcUrl = '<YOUR_GRPC_URL>'
// export const grpcToken = '<YOUR_GRPC_TOKEN>'
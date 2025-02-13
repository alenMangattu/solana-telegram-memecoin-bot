package com.lunax.telegramBot.utils;

import org.p2p.solanaj.core.AccountMeta;
import org.p2p.solanaj.core.PublicKey;
import org.p2p.solanaj.core.TransactionInstruction;
import com.mmorrell.metaplex.model.Metadata;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MetadataInstruction {
    private static final byte CREATE_METADATA_ACCOUNT_INSTRUCTION = 0;

    public static TransactionInstruction createMetadataAccount(
            PublicKey metadata,
            PublicKey mint,
            PublicKey mintAuthority,
            PublicKey payer,
            PublicKey updateAuthority,
            Metadata metadataData,
            boolean isMutable) {
        
        List<AccountMeta> keys = new ArrayList<>();
        keys.add(new AccountMeta(metadata, false, true));
        keys.add(new AccountMeta(mint, false, false));
        keys.add(new AccountMeta(mintAuthority, true, false));
        keys.add(new AccountMeta(payer, true, true));
        keys.add(new AccountMeta(updateAuthority, false, false));
        keys.add(new AccountMeta(new PublicKey("11111111111111111111111111111111"), false, false)); // System Program
        keys.add(new AccountMeta(new PublicKey("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"), false, false)); // Token Program
        
        byte[] data = serializeMetadata(metadataData, isMutable);
        
        return new TransactionInstruction(
            new PublicKey("metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s"),
            keys,
            data
        );
    }

    private static byte[] serializeMetadata(Metadata metadata, boolean isMutable) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             DataOutputStream dos = new DataOutputStream(bos)) {
            
            // Instruction discriminator for create metadata
            dos.writeByte(0); // CREATE_METADATA_ACCOUNT_V3 instruction
            
            // Data struct
            writeString(dos, metadata.getName());
            writeString(dos, metadata.getSymbol());
            writeString(dos, metadata.getUri());
            dos.writeShort(0); // seller fee basis points
            
            // Empty creators array
            dos.writeBoolean(false); // Option<Vec<Creator>> = None
            
            // Collection
            dos.writeBoolean(false); // Option<Collection> = None
            
            // Uses
            dos.writeBoolean(false); // Option<Uses> = None
            
            // Is mutable
            dos.writeBoolean(isMutable);
            
            // Collection details
            dos.writeBoolean(false); // Option<CollectionDetails> = None
            
            // Token standard (0 = NonFungible)
            dos.writeInt(2); // Fungible
            
            return bos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize metadata: " + e.getMessage());
        }
    }
    
    private static void writeString(DataOutputStream dos, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        dos.writeInt(bytes.length);
        dos.write(bytes);
    }
} 
package com.lunax.telegramBot.utils;

import io.ipfs.api.IPFS;
import io.ipfs.api.MerkleNode;
import io.ipfs.api.NamedStreamable;
import io.ipfs.multihash.Multihash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class IpfsUtilsTest {

    private IPFS mockIpfs;

    @BeforeEach
    public void setUp() {
        mockIpfs = Mockito.mock(IPFS.class);
        IpfsUtils.initializeIpfs(); // Initialize IPFS instance
    }

    @Test
    public void testAddFile() throws IOException {
        // Arrange
        String filePath = "test.txt";
        NamedStreamable.FileWrapper mockFile = new NamedStreamable.FileWrapper(new File(filePath));
        // Create a mock MerkleNode with the hash
        List<MerkleNode> mockNode = mockIpfs.add(mockFile);
        for (MerkleNode node : mockNode) {
            System.out.println(node.hash);
        }
        System.out.println("-------------------------------- DONE");
        // when(mockNode.hash).thenReturn(mockHash);

        // // Update the mockIpfs to use the correct Multihash type
        // when(mockIpfs.add(mockFile)).thenReturn(Collections.singletonList(mockNode));

        // // Act
        // String result = IpfsUtils.addFile(filePath);

        // // Assert
        // assertEquals("QmTestHash", result);
        // verify(mockIpfs).add(mockFile);
    }

    @Test
    public void testGetFile() throws IOException {
        // Arrange
        Multihash mockHash = Multihash.fromBase58("QmTestHash");
        byte[] mockData = "Test data".getBytes();
        
        when(mockIpfs.cat(mockHash)).thenReturn(mockData);

        // Act
        byte[] result = IpfsUtils.getFile(mockHash);

        // Assert
        assertArrayEquals(mockData, result);
        verify(mockIpfs).cat(mockHash);
    }

    @Test
    public void testAddFileThrowsIOException() throws IOException {
        // Arrange
        String filePath = "test.txt";
        NamedStreamable.FileWrapper mockFile = new NamedStreamable.FileWrapper(new File(filePath));
        
        when(mockIpfs.add(mockFile)).thenThrow(new IOException("Failed to add file"));

        // Act
        String result = IpfsUtils.addFile(filePath);

        // Assert
        assertNull(result);
        verify(mockIpfs).add(mockFile);
    }

    @Test
    public void testGetFileThrowsIOException() throws IOException {
        // Arrange
        Multihash mockHash = Multihash.fromBase58("QmTestHash");
        
        when(mockIpfs.cat(mockHash)).thenThrow(new IOException("Failed to retrieve file"));

        // Act
        byte[] result = IpfsUtils.getFile(mockHash);

        // Assert
        assertNull(result);
        verify(mockIpfs).cat(mockHash);
    }
} 
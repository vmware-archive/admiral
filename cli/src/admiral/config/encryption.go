/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 *
 * This product is licensed to you under the Apache License, Version 2.0 (the "License").
 * You may not use this product except in compliance with the License.
 *
 * This product may include a number of subcomponents with separate copyright notices
 * and license terms. Your use of these subcomponents is subject to the terms and
 * conditions of the subcomponent's license, as noted in the LICENSE file.
 */

package config

import (
	"crypto/aes"
	"crypto/cipher"
	"encoding/base64"
	"errors"
	"fmt"
	"io/ioutil"
)

// Encrypt is encrypting string the same way how passwords are being
// encrypted in Admiral.
func Encrypt(plainText string, keyString string) (string, error) {
	key, err := loadEncryptionKey(keyString)
	if err != nil {
		return "", err
	}
	paddedText := pad(aes.BlockSize, []byte(plainText))
	encrypted, err := encryptAES(key, paddedText)
	if err != nil {
		return "", err
	}
	return base64.StdEncoding.EncodeToString(encrypted), nil
}

// encryptAES is function invoked from Encrypt doing the
// encryption part of the string.
func encryptAES(key, data []byte) ([]byte, error) {
	block, err := aes.NewCipher(key[aes.BlockSize:])
	if err != nil {
		return nil, err
	}

	output := make([]byte, len(data))
	iv := key[:aes.BlockSize]

	stream := cipher.NewCBCEncrypter(block, iv)
	stream.CryptBlocks(output, data)
	return output, nil
}

// Encrypt is decrypting string the same way how passwords are being
// decrypted in Admiral.
func Decrypt(cryptoText string, keyString string) (string, error) {
	key, err := loadEncryptionKey(keyString)
	if err != nil {
		return "", err
	}
	encrypted, err := base64.StdEncoding.DecodeString(cryptoText)
	if err != nil {
		return "", err
	}
	if len(encrypted) < aes.BlockSize {
		return "", fmt.Errorf("cipherText too short. It decodes to %v bytes but the minimum length is 16", len(encrypted))
	}

	decrypted, err := decryptAES(key, encrypted)
	if err != nil {
		return "", err
	}
	return string(decrypted), nil
}

// decryptAES is function invoked from Decrypt doing the
// decryption part of the string.
func decryptAES(key, data []byte) ([]byte, error) {
	iv := key[:aes.BlockSize]

	block, err := aes.NewCipher(key[aes.BlockSize:])
	if err != nil {
		return nil, err
	}
	stream := cipher.NewCBCDecrypter(block, iv)
	stream.CryptBlocks(data, data)
	data, err = unpad(aes.BlockSize, data)
	if err != nil {
		return nil, err
	}
	return data, nil
}

// loadEncryptionKey is loading the needed encryption key from file.
func loadEncryptionKey(filePath string) ([]byte, error) {
	fileBytes, err := ioutil.ReadFile(filePath)
	if err != nil {
		return nil, err
	}
	return fileBytes, nil
}

var (
	ErrorPaddingNotFound      = errors.New("Bad PKCS#7 padding - not padded")
	ErrorPaddingNotAMultiple  = errors.New("Bad PKCS#7 padding - not a multiple of blocksize")
	ErrorPaddingTooLong       = errors.New("Bad PKCS#7 padding - too long")
	ErrorPaddingTooShort      = errors.New("Bad PKCS#7 padding - too short")
	ErrorPaddingNotAllTheSame = errors.New("Bad PKCS#7 padding - not all the same")
)

func pad(n int, buf []byte) []byte {
	if n <= 1 || n >= 256 {
		panic("bad multiple")
	}
	length := len(buf)
	padding := n - (length % n)
	for i := 0; i < padding; i++ {
		buf = append(buf, byte(padding))
	}
	if (len(buf) % n) != 0 {
		panic("padding failed")
	}
	return buf
}

func unpad(n int, buf []byte) ([]byte, error) {
	if n <= 1 || n >= 256 {
		panic("bad multiple")
	}
	length := len(buf)
	if length == 0 {
		return nil, ErrorPaddingNotFound
	}
	if (length % n) != 0 {
		return nil, ErrorPaddingNotAMultiple
	}
	padding := int(buf[length-1])
	if padding > n {
		return nil, ErrorPaddingTooLong
	}
	if padding == 0 {
		return nil, ErrorPaddingTooShort
	}
	for i := 0; i < padding; i++ {
		if buf[length-1-i] != byte(padding) {
			return nil, ErrorPaddingNotAllTheSame
		}
	}
	return buf[:length-padding], nil
}

import { gcm } from '@noble/ciphers/aes.js'
import { sha256 } from '@noble/hashes/sha2.js'

const encoder = new TextEncoder()
const decoder = new TextDecoder()

const subtleCrypto = typeof crypto !== 'undefined' && crypto.subtle ? crypto.subtle : undefined

const encryptWithNativeGcm = async (rawKey: Uint8Array, iv: Uint8Array, data: Uint8Array): Promise<Uint8Array> => {
  const key = await subtleCrypto!.importKey('raw', rawKey, 'AES-GCM', false, ['encrypt'])
  return new Uint8Array(await subtleCrypto!.encrypt({ name: 'AES-GCM', iv }, key, data))
}

const decryptWithNativeGcm = async (rawKey: Uint8Array, iv: Uint8Array, data: Uint8Array): Promise<Uint8Array> => {
  const key = await subtleCrypto!.importKey('raw', rawKey, 'AES-GCM', false, ['decrypt'])
  return new Uint8Array(await subtleCrypto!.decrypt({ name: 'AES-GCM', iv }, key, data))
}

const base64ToBytes = (value: string): Uint8Array => {
  const binary = atob(value)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

const bytesToBase64 = (bytes: Uint8Array): string => {
  let binary = ''
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return btoa(binary)
}

export interface FileKey {
  raw: Uint8Array
  base64: string
}

export const generateFileKey = async (): Promise<FileKey> => {
  const raw = crypto.getRandomValues(new Uint8Array(32))
  return {
    raw,
    base64: bytesToBase64(raw)
  }
}

const FILE_MAGIC_V2 = new Uint8Array([0x4e, 0x4d, 0x4c, 0x45]) // NMLE

export const encryptBlobWithFileKey = async (data: Uint8Array, fileKey: FileKey): Promise<Uint8Array> => {
  const iv = crypto.getRandomValues(new Uint8Array(12))
  const ciphertext = subtleCrypto
    ? await encryptWithNativeGcm(fileKey.raw, iv, data)
    : gcm(fileKey.raw, iv).encrypt(data)
  const fingerprint = sha256(fileKey.raw)
  const header = new Uint8Array(FILE_MAGIC_V2.length + 1 + fingerprint.length + iv.length)
  let offset = 0
  header.set(FILE_MAGIC_V2, offset); offset += FILE_MAGIC_V2.length
  header[offset] = 2; offset += 1
  header.set(fingerprint, offset); offset += fingerprint.length
  header.set(iv, offset)
  const result = new Uint8Array(header.length + ciphertext.length)
  result.set(header, 0)
  result.set(ciphertext, header.length)
  return result
}

export const encryptBlobWithKeyBase64 = async (data: Uint8Array, keyBase64: string): Promise<Uint8Array> => {
  const raw = base64ToBytes(keyBase64.trim())
  if (raw.length !== 32) {
    throw new Error('文件密钥格式不正确，请检查是否完整复制')
  }
  return encryptBlobWithFileKey(data, { raw, base64: keyBase64.trim() })
}

// 兼容旧版账号密钥格式（NMLE v1），旧文件使用保存的账号密钥解密
const decryptBlobWithLegacyAccountKey = async (blob: Uint8Array, accountKeyBase64: string): Promise<Uint8Array> => {
  let offset = FILE_MAGIC_V2.length + 1
  const iv = blob.slice(offset, offset + 12); offset += 12
  if (blob.length < offset + 2 + 12 + 16) {
    throw new Error('加密文件数据不完整')
  }
  const wrappedKeyLength = (blob[offset] << 8) | blob[offset + 1]; offset += 2
  const wrapIv = blob.slice(offset, offset + 12); offset += 12
  if (blob.length < offset + wrappedKeyLength + 16) {
    throw new Error('加密文件数据不完整')
  }
  const wrappedFileKey = blob.slice(offset, offset + wrappedKeyLength)
  const raw = base64ToBytes(accountKeyBase64.trim())
  if (raw.length !== 32) {
    throw new Error('文件密钥格式不正确，请检查是否完整复制')
  }
  let fileKeyRaw: Uint8Array
  try {
    fileKeyRaw = subtleCrypto
      ? await decryptWithNativeGcm(raw, wrapIv, wrappedFileKey)
      : gcm(raw, wrapIv).decrypt(wrappedFileKey)
  } catch {
    throw new Error('旧版加密文件需要使用当时保存的账号密钥，请检查输入')
  }
  let plaintext: Uint8Array
  try {
    plaintext = subtleCrypto
      ? await decryptWithNativeGcm(fileKeyRaw, iv, blob.slice(offset + wrappedKeyLength))
      : gcm(fileKeyRaw, iv).decrypt(blob.slice(offset + wrappedKeyLength))
  } catch {
    throw new Error('旧版加密文件需要使用当时保存的账号密钥，请检查输入')
  }
  return plaintext
}

export const decryptBlobWithFileKey = async (blob: Uint8Array, keyBase64: string): Promise<Uint8Array> => {
  const headerLength = FILE_MAGIC_V2.length + 1 + 32 + 12
  if (blob.length < headerLength + 16) {
    throw new Error('加密文件数据不完整')
  }
  for (let i = 0; i < FILE_MAGIC_V2.length; i++) {
    if (blob[i] !== FILE_MAGIC_V2[i]) {
      throw new Error('不是有效的端到端加密文件')
    }
  }
  if (blob[FILE_MAGIC_V2.length] === 1) {
    return decryptBlobWithLegacyAccountKey(blob, keyBase64)
  }
  if (blob[FILE_MAGIC_V2.length] !== 2) {
    throw new Error('不支持的加密文件版本')
  }
  const raw = base64ToBytes(keyBase64.trim())
  if (raw.length !== 32) {
    throw new Error('文件密钥格式不正确，请检查是否完整复制')
  }
  const fingerprint = sha256(raw)
  let offset = FILE_MAGIC_V2.length + 1
  const headerFingerprint = blob.slice(offset, offset + 32); offset += 32
  for (let i = 0; i < 32; i++) {
    if (fingerprint[i] !== headerFingerprint[i]) {
      throw new Error('文件密钥不正确，请检查输入')
    }
  }
  const iv = blob.slice(offset, offset + 12); offset += 12
  return subtleCrypto
    ? await decryptWithNativeGcm(raw, iv, blob.slice(offset))
    : gcm(raw, iv).decrypt(blob.slice(offset))
}

export const decodeText = (bytes: Uint8Array) => decoder.decode(bytes)

export const encodeText = (text: string) => encoder.encode(text)

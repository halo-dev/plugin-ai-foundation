import { generateId } from './id'
import type { FilePart } from './types'

export type BrowserFileInput = File | FileList | Iterable<File> | ArrayLike<File>

type FilePartOption<T> = T | ((file: File, index: number) => T)

export interface FilePartFromFileOptions {
  id?: FilePartOption<string | undefined>
  title?: FilePartOption<string | undefined>
  mediaType?: FilePartOption<string | undefined>
  providerMetadata?: FilePartOption<Record<string, unknown> | undefined>
}

export type FilePartsFromFilesOptions = FilePartFromFileOptions

export async function filePartFromFile(
  file: File,
  options: FilePartFromFileOptions = {},
): Promise<FilePart> {
  return createFilePart(file, 0, options)
}

export async function filePartsFromFiles(
  files: BrowserFileInput,
  options: FilePartsFromFilesOptions = {},
): Promise<FilePart[]> {
  return Promise.all(normalizeFiles(files).map((file, index) => createFilePart(file, index, options)))
}

async function createFilePart(
  file: File,
  index: number,
  options: FilePartFromFileOptions,
): Promise<FilePart> {
  const id = resolveOption(options.id, file, index) ?? generateId('file')
  return {
    type: 'file',
    id,
    fileId: id,
    title: resolveOption(options.title, file, index) ?? file.name,
    mediaType: (resolveOption(options.mediaType, file, index) ?? file.type) || 'application/octet-stream',
    data: await toBase64(file),
    providerMetadata: resolveOption(options.providerMetadata, file, index),
  }
}

function normalizeFiles(files: BrowserFileInput): File[] {
  if (isFileLike(files)) {
    return [files]
  }
  if (isArrayLike(files)) {
    return Array.from(files)
  }
  return Array.from(files)
}

function isFileLike(value: unknown): value is File {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as File).arrayBuffer === 'function' &&
    typeof (value as File).name === 'string'
  )
}

function isArrayLike(value: unknown): value is ArrayLike<File> {
  return (
    typeof value === 'object' &&
    value !== null &&
    'length' in value &&
    typeof (value as ArrayLike<File>).length === 'number'
  )
}

function resolveOption<T>(
  option: FilePartOption<T> | undefined,
  file: File,
  index: number,
): T | undefined {
  return typeof option === 'function'
    ? (option as (file: File, index: number) => T)(file, index)
    : option
}

async function toBase64(file: File): Promise<string> {
  const bytes = new Uint8Array(await file.arrayBuffer())
  let binary = ''
  const chunkSize = 0x8000
  for (let index = 0; index < bytes.length; index += chunkSize) {
    binary += String.fromCharCode(...bytes.subarray(index, index + chunkSize))
  }
  if (typeof globalThis.btoa !== 'function') {
    throw new Error('A base64 encoder is not available in this runtime.')
  }
  return globalThis.btoa(binary)
}

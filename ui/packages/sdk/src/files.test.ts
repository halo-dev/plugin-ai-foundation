import { describe, expect, it } from '@rstest/core'
import { filePartFromFile, filePartsFromFiles } from './files'

describe('file part helpers', () => {
  it('converts a browser File to a base64 file part', async () => {
    const file = new File(['hello'], 'hello.txt', { type: 'text/plain' })

    await expect(filePartFromFile(file, { id: 'file-1' })).resolves.toEqual({
      type: 'file',
      id: 'file-1',
      fileId: 'file-1',
      title: 'hello.txt',
      mediaType: 'text/plain',
      data: 'aGVsbG8=',
      providerMetadata: undefined,
    })
  })

  it('converts FileList-like input with per-file options', async () => {
    const files = {
      0: new File(['one'], 'one.txt', { type: 'text/plain' }),
      1: new File(['two'], 'two.bin'),
      length: 2,
    } as unknown as FileList

    const parts = await filePartsFromFiles(files, {
      id: (_file, index) => `file-${index}`,
      title: (file) => `Attachment: ${file.name}`,
      providerMetadata: (file) => ({ name: file.name }),
    })

    expect(parts).toEqual([
      {
        type: 'file',
        id: 'file-0',
        fileId: 'file-0',
        title: 'Attachment: one.txt',
        mediaType: 'text/plain',
        data: 'b25l',
        providerMetadata: { name: 'one.txt' },
      },
      {
        type: 'file',
        id: 'file-1',
        fileId: 'file-1',
        title: 'Attachment: two.bin',
        mediaType: 'application/octet-stream',
        data: 'dHdv',
        providerMetadata: { name: 'two.bin' },
      },
    ])
  })
})

import { create } from 'zustand'
import type { FileItem, FileListResponse } from '@/api/types'

interface FileState {
  files: FileItem[]
  currentFile: FileItem | null
  totalCount: number
  currentPage: number
  pageSize: number
  loading: boolean
  setFiles: (files: FileItem[]) => void
  setFileList: (response: FileListResponse) => void
  setCurrentFile: (file: FileItem | null) => void
  addFile: (file: FileItem) => void
  updateFile: (fileId: string, updates: Partial<FileItem>) => void
  removeFile: (fileId: string) => void
  setLoading: (loading: boolean) => void
  setCurrentPage: (page: number) => void
}

export const useFileStore = create<FileState>((set) => ({
  files: [],
  currentFile: null,
  totalCount: 0,
  currentPage: 1,
  pageSize: 20,
  loading: false,

  setFiles: (files) => set({ files }),

  setFileList: (response) =>
    set({
      files: response.content,
      totalCount: response.totalElements,
      currentPage: response.number + 1,
      pageSize: response.size,
    }),

  setCurrentFile: (file) => set({ currentFile: file }),

  addFile: (file) => set((state) => ({ files: [file, ...state.files] })),

  updateFile: (fileId, updates) =>
    set((state) => ({
      files: state.files.map((f) => (f.id === fileId ? { ...f, ...updates } : f)),
      currentFile: state.currentFile?.id === fileId ? { ...state.currentFile, ...updates } : state.currentFile,
    })),

  removeFile: (fileId) =>
    set((state) => ({
      files: state.files.filter((f) => f.id !== fileId),
      currentFile: state.currentFile?.id === fileId ? null : state.currentFile,
    })),

  setLoading: (loading) => set({ loading }),

  setCurrentPage: (page) => set({ currentPage: page }),
}))
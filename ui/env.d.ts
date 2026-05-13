/// <reference types="@rsbuild/core/types" />
/// <reference types="unplugin-icons/types/vue" />

declare const Dialog: {
  warning: (options: { title?: string; description?: string; onConfirm?: () => void | Promise<void> }) => void
  confirm: (options: { title?: string; description?: string; onConfirm?: () => void | Promise<void> }) => void
}

declare const Toast: {
  success: (message: string) => void
  warning: (message: string) => void
  error: (message: string) => void
}

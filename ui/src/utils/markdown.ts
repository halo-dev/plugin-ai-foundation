import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'

const markdown = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

export function renderMarkdown(content: string) {
  return DOMPurify.sanitize(markdown.render(content))
}

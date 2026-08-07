interface LoadingProps {
  size?: 'sm' | 'md' | 'lg'
  text?: string
  inline?: boolean
}

export const Loading = ({ size = 'md', text, inline = false }: LoadingProps) => {
  const sizeStyles = {
    sm: 'h-4 w-4',
    md: 'h-6 w-6',
    lg: 'h-8 w-8',
  }

  if (inline) {
    return (
      <span className="flex items-center gap-2">
        <svg className={`animate-spin ${sizeStyles[size]}`} viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
        </svg>
        {text && <span className="text-sm text-neutral-500">{text}</span>}
      </span>
    )
  }

  return (
    <div className="flex flex-col items-center justify-center py-8">
      <svg className={`animate-spin ${sizeStyles[size]}`} viewBox="0 0 24 24">
        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
        <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z" />
      </svg>
      {text && <p className="mt-4 text-sm text-neutral-500">{text}</p>}
    </div>
  )
}
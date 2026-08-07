import type { ReactNode } from 'react'

interface InputProps {
  label?: string
  error?: string
  prefix?: ReactNode
  suffix?: ReactNode
  type?: string
  placeholder?: string
  value?: string | number
  onChange?: React.ChangeEventHandler<HTMLInputElement>
  className?: string
  required?: boolean
}

export const Input = ({ label, error, prefix, suffix, className = '', ...props }: InputProps) => {
  return (
    <div className="space-y-1">
      {label && <label className="block text-sm font-medium text-text-primary">{label}</label>}
      <div className="relative">
        {prefix && (
          <span className="absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400">
            {prefix}
          </span>
        )}
        <input
          className={`w-full px-4 py-2.5 border border-neutral-200 rounded-button text-sm text-text-primary placeholder-neutral-400 focus:outline-none focus:ring-2 focus:ring-accent-blue focus:border-transparent transition-all duration-200 ${
            error ? 'border-red-500 focus:ring-red-500' : ''
          } ${prefix ? 'pl-10' : ''} ${suffix ? 'pr-10' : ''} ${className}`}
          {...props}
        />
        {suffix && (
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-neutral-400">
            {suffix}
          </span>
        )}
      </div>
      {error && <p className="text-sm text-red-500">{error}</p>}
    </div>
  )
}

interface TextAreaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string
  error?: string
}

export const TextArea = ({ label, error, className = '', ...props }: TextAreaProps) => {
  return (
    <div className="space-y-1">
      {label && <label className="block text-sm font-medium text-neutral-700">{label}</label>}
      <textarea
        className={`w-full px-4 py-2.5 border border-neutral-200 rounded-button text-sm text-text-primary placeholder-neutral-400 focus:outline-none focus:ring-2 focus:ring-accent-blue focus:border-transparent transition-all duration-200 resize-none ${
          error ? 'border-red-500 focus:ring-red-500' : ''
        } ${className}`}
        {...props}
      />
      {error && <p className="text-sm text-red-500">{error}</p>}
    </div>
  )
}
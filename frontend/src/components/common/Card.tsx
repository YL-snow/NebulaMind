import type { ReactNode } from 'react'

interface CardProps {
  children: ReactNode
  className?: string
  hoverable?: boolean
  onClick?: () => void
}

export const Card = ({ children, className = '', hoverable = false, onClick }: CardProps) => {
  return (
    <div
      className={`bg-white border border-neutral-200 shadow-card ${
        hoverable ? 'cursor-pointer hover:shadow-card-hover hover:border-accent-blue/30 transition-all duration-300' : ''
      } ${className}`}
      onClick={onClick}
    >
      {children}
    </div>
  )
}

interface CardHeaderProps {
  children: ReactNode
  className?: string
  onClick?: () => void
}

export const CardHeader = ({ children, className = '', onClick }: CardHeaderProps) => {
  return (
    <div 
      className={`px-6 py-4 border-b border-neutral-200 ${className} ${onClick ? 'cursor-pointer' : ''}`}
      onClick={onClick}
    >
      {children}
    </div>
  )
}

interface CardBodyProps {
  children: ReactNode
  className?: string
}

export const CardBody = ({ children, className = '' }: CardBodyProps) => {
  return <div className={`px-6 py-4 ${className}`}>{children}</div>
}

interface CardFooterProps {
  children: ReactNode
  className?: string
}

export const CardFooter = ({ children, className = '' }: CardFooterProps) => {
  return <div className={`px-6 py-4 border-t border-neutral-200 bg-neutral-50 ${className}`}>{children}</div>
}
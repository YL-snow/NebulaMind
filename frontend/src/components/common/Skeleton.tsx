interface SkeletonProps {
  className?: string
  variant?: 'text' | 'rect' | 'circle'
}

export const Skeleton = ({ className = '', variant = 'rect' }: SkeletonProps) => {
  const baseStyles = 'animate-pulse bg-neutral-100'

  const variantStyles = {
    text: 'h-4 rounded w-full',
    rect: 'rounded-lg',
    circle: 'rounded-full',
  }

  return <div className={`${baseStyles} ${variantStyles[variant]} ${className}`} />
}
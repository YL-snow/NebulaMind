interface ProgressBarProps {
  progress: number
  showLabel?: boolean
  size?: 'sm' | 'md' | 'lg'
  color?: 'primary' | 'secondary' | 'danger'
  striped?: boolean
  animated?: boolean
  className?: string
}

export const ProgressBar = ({
  progress,
  showLabel = false,
  size = 'md',
  color = 'primary',
  striped = false,
  animated = false,
  className = '',
}: ProgressBarProps) => {
  const sizeStyles = {
    sm: 'h-1',
    md: 'h-2',
    lg: 'h-3',
  }

  const colorStyles = {
    primary: 'bg-accent-blue',
    secondary: 'bg-secondary-500',
    danger: 'bg-red-500',
  }

  return (
    <div className={`space-y-1 ${className}`}>
      <div className={`w-full bg-neutral-100 rounded-full overflow-hidden ${sizeStyles[size]}`}>
        <div
          className={`${colorStyles[color]} transition-all duration-500 ease-out rounded-full ${
            striped ? 'bg-gradient-to-r' : ''
          } ${animated ? 'animate-pulse' : ''}`}
          style={{ width: `${Math.min(100, Math.max(0, progress))}%` }}
        />
      </div>
      {showLabel && (
        <span className="text-xs text-text-secondary">{Math.round(progress)}%</span>
      )}
    </div>
  )
}
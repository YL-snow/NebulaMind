/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        base: '#F7F7F5',
        'bg-card': '#FFFFFF',
        'bg-elevated': '#1A1A1A',
        'text-primary': '#141413',
        'text-secondary': '#5A5A5A',
        'text-inverse': '#FFFFFF',
        'border-light': '#EBEAE7',
        accent: {
          blue: '#5977a5',
          amber: '#5977a5',
        },
        primary: {
          50: '#f0f4f9',
          100: '#dce5f0',
          200: '#b8cde1',
          300: '#8aadcc',
          400: '#5c8bb5',
          500: '#2B4C7E',
          600: '#233d66',
          700: '#1b2f4f',
          800: '#132238',
          900: '#0b1520',
        },
        neutral: {
          50: '#FAFAF8',
          100: '#F5F4F2',
          200: '#EBEAE7',
          300: '#D4D3D0',
          400: '#A3A2A0',
          500: '#737270',
          600: '#5A5A5A',
          700: '#404040',
          800: '#141413',
          900: '#0d0d0c',
        },
        // 安全页颜色（降低饱和度、增加灰度）
        red: {
          50: '#faf0f0',
          500: '#b84a4aff',
          600: '#9a3a3a',
        },
        yellow: {
          50: '#faf6f0',
          500: '#decc59',
          600: '#e9c706',
        },
        green: {
          50: '#f0f6f0',
          500: '#4a9a5a',
          600: '#3a7a4a',
        },
        blue: {
          50: '#f0f4fa',
          500: '#4a7ab8',
          600: '#3a629a',
        },
      },
      borderRadius: {
        card: '12px',
        button: '0',
      },
      boxShadow: {
        card: '0 4px 20px rgba(0, 0, 0, 0.03)',
        'card-hover': '0 8px 30px rgba(0, 0, 0, 0.06)',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'sans-serif'],
      },
    },
  },
  plugins: [],
}

/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // GuardianOS palette - ajustar según preferencias de dark theme
        primary: {
          50: '#f0f9ff',
          500: '#0ea5e9',
          900: '#0c4a6e',
        },
        cyber: {
          bg: '#0a0a0f',
          surface: '#12121a',
          border: '#2a2a3a',
          text: '#e0e0ff',
          accent: '#00d4aa'
        }
      }
    },
  },
  plugins: [],
}

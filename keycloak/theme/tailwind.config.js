import colors from 'tailwindcss/colors';

/**
 * @type { import('tailwindcss').Config }
 */
export default {
  content: ['./theme/**/*.ftl'],
  experimental: {
    optimizeUniversalDefaults: true,
  },
  plugins: [require('@tailwindcss/forms')],
  theme: {
    extend: {
      colors: {
        secondary: colors.gray,
        primary: {
          50: '#cceaed',
          100: '#99d5dc',
          200: '#66c1ca',
          300: '#33acb9',
          400: '#1aa1b0',
          500: '#0097a7',
          600: '#027d78',
          700: '#007986',
          800: '#006a75',
          900: '#005b64',
        },
      },
      provider: {
        apple: '#000000',
        bitbucket: '#0052CC',
        discord: '#5865F2',
        facebook: '#1877F2',
        github: '#181717',
        gitlab: '#FC6D26',
        google: '#4285F4',
        instagram: '#E4405F',
        linkedin: '#0A66C2',
        microsoft: '#5E5E5E',
        oidc: '#F78C40',
        openshift: '#EE0000',
        paypal: '#00457C',
        slack: '#4A154B',
        stackoverflow: '#F58025',
        twitter: '#1DA1F2',
      },
    },
  },
}

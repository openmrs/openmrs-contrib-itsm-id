import { defineConfig } from 'vite';

export default defineConfig({
  build: {
    emptyOutDir: false,
    rollupOptions: {
      input: {
        'login/resources/dist/index': 'src/index.ts',
        'login/resources/dist/recoveryCodes': 'src/data/recoveryCodes.ts',
        'login/resources/dist/webAuthnAuthenticate': 'src/data/webAuthnAuthenticate.ts',
        'login/resources/dist/webAuthnRegister': 'src/data/webAuthnRegister.ts',
        'account/resources/dist/landing': 'src/account/landing.ts',
      },
      output: {
        dir: 'theme/openmrs',
        entryFileNames: '[name].js',
        assetFileNames: 'login/resources/dist/[name][extname]',
        chunkFileNames: 'login/resources/dist/[name]-[hash].js',
      },
    },
  },
});

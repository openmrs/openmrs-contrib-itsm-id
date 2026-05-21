import type { Alpine } from 'alpinejs';
import type Keycloak from 'keycloak-js';

declare global {
  interface Window {
    Alpine: Alpine;
    result?: PublicKeyCredential;
    __OPENMRS_ACCOUNT__: {
      authUrl: string;
      realm: string;
      entryScript: string | null;
    };
    __openmrsFeatures: Record<string, boolean>;
    keycloak?: Keycloak;
  }
}

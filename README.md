# Filetalino

Filetalino is an open-source Android file manager for Android 5.0 and later.

## Added features

- Native Google Drive support
- Native OneDrive support
- Native Dropbox support
- OAuth2 authentication with PKCE browser sign-in
- In-app OAuth client configuration
- Build-time OAuth credential configuration through `local.properties`
- Cloud account storage and management

## Cloud storage setup

Filetalino supports Google Drive, OneDrive, and Dropbox.

To add a cloud account:

1. Open the navigation drawer.
2. Tap **Add storage**.
3. Select Google Drive, OneDrive, or Dropbox.
4. Enter the provider credentials.
5. Sign in through the browser.
6. Tap **Connect and add**.

OAuth providers use `http://127.0.0.1:53682/` as the loopback redirect URI.

## Build-time credentials

Add the following values to `local.properties` for credentials to be pre-filled in the app:

```properties
GDRIVE_CLIENT_ID=your-google-client-id.apps.googleusercontent.com
GDRIVE_CLIENT_SECRET=GOCSPX-your-google-secret
ONEDRIVE_CLIENT_ID=your-onedrive-application-client-id
ONEDRIVE_CLIENT_SECRET=
DROPBOX_CLIENT_ID=your-dropbox-app-key
DROPBOX_CLIENT_SECRET=your-dropbox-app-secret
```

Install a debug build with:

```bash
./gradlew installDebug
```

## License

Filetalino retains the GNU General Public License v3.0.

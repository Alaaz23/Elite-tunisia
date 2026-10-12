# Test & Build Commands (frontend)

Run these commands after installing a compatible Node.js (v22.22.3, v24.15.0 or >=26). Do not run them on Node v18.

Windows (CMD):
```cmd
cd frontend
npm ci
npm run test
npm run build -- --configuration production
npm run e2e
```

Unix / WSL:
```bash
cd frontend
npm ci
npm run test
npm run build -- --configuration production
npm run e2e
```

Notes:
- `npm ci` will install exact versions from `package-lock.json`.
- `npm run e2e` requires Playwright browsers installed; if missing run `npx playwright install --with-deps`.
- Preserve `package-lock.json` and do not downgrade dependencies to bypass Node constraints.

## 1. Backend RBAC Removal

- [x] 1.1 Delete `app/src/main/resources/extensions/roleTemplate.yaml`
- [x] 1.2 Verify plugin.yaml does not reference `roleTemplate.yaml` (if it does, remove the reference)

## 2. Frontend Permission Removal

- [x] 2.1 Remove `permissions: ['plugin:ai-foundation:manage']` from the route meta in `ui/src/index.ts`

## 3. Verification

- [x] 3.1 Rebuild the plugin with `./gradlew build` and verify no build errors
- [x] 3.2 Restart dev server and confirm the "AI 模型配置" menu is visible only to super-admin users

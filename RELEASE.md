# Release Process

This document describes how to release LangChain4Clj to Clojars and create GitHub releases.

## Prerequisites

### For Manual Releases

1. **Clojars Account**
   - Create account at https://clojars.org
   - Generate deploy token at https://clojars.org/tokens

2. **Local Environment Variables**
   ```bash
   export CLOJARS_USERNAME=your-username
   export CLOJARS_PASSWORD=your-deploy-token
   ```

### For Automated Releases (Recommended)

1. **GitHub Repository Secrets**
   - Go to repository Settings > Secrets and variables > Actions
   - Add `CLOJARS_USERNAME` (your Clojars username)
   - Add `CLOJARS_PASSWORD` (your Clojars deploy token)

## Release Workflow

### Automated Release (Recommended)

The easiest way to release is using GitHub Actions:

1. **Update version in `build.clj`**
   ```clojure
   (def version "1.0.4") ; Update to new version
   ```

2. **Update CHANGELOG.md**
   - Add release notes for the new version
   - Follow Keep a Changelog format

3. **Commit and push changes**
   ```bash
   git add build.clj CHANGELOG.md
   git commit -m "Release version 1.0.4"
   git push origin main
   ```

4. **Create and push a tag**
   ```bash
   git tag v1.0.4
   git push origin v1.0.4
   ```

5. **Wait for GitHub Actions**
   - The `release.yml` workflow will automatically:
     - Run all tests
     - Build the JAR
     - Deploy to Clojars
     - Create a GitHub release with the JAR attached

### Manual Release

If you prefer to release manually:

1. **Update version in `build.clj`**
   ```clojure
   (def version "1.0.4")
   ```

2. **Update CHANGELOG.md**

3. **Run tests**
   ```bash
   clojure -M:test
   ```

4. **Build and deploy**
   ```bash
   # Test the build locally first
   clojure -T:build jar
   
   # Deploy to Clojars
   clojure -T:build deploy
   ```

5. **Create Git tag**
   ```bash
   git tag v1.0.4
   git push origin v1.0.4
   ```

6. **Create GitHub Release**
   - Go to https://github.com/nandoolle/langchain4clj/releases/new
   - Select the tag you just created
   - Add release notes
   - Attach the JAR from `target/` directory
   - Publish release

## Version Numbering

Follow Semantic Versioning (SemVer):

- **MAJOR** (1.x.x) - Breaking changes
- **MINOR** (x.1.x) - New features, backwards compatible
- **PATCH** (x.x.1) - Bug fixes, backwards compatible

## Build Commands

Useful build commands for local development:

```bash
# Run tests
clojure -M:test

# Clean build artifacts
clojure -T:build clean

# Build JAR
clojure -T:build jar

# Install locally (for testing before release)
clojure -T:build install

# Deploy to Clojars
clojure -T:build deploy

# Full CI pipeline
clojure -T:build ci
```

## Verifying Release

After releasing to Clojars:

1. **Check Clojars**
   - Visit https://clojars.org/io.github.nandoolle/langchain4clj
   - Verify new version is listed

2. **Test Installation**
   ```clojure
   ;; In a test project's deps.edn
   {:deps {io.github.nandoolle/langchain4clj {:mvn/version "1.0.4"}}}
   ```

3. **Check GitHub Release**
   - Visit https://github.com/nandoolle/langchain4clj/releases
   - Verify release notes and JAR attachment

## Troubleshooting

### Deploy Fails with Authentication Error

Make sure your Clojars credentials are set:
```bash
echo $CLOJARS_USERNAME
echo $CLOJARS_PASSWORD
```

### JAR Build Fails

1. Clean and try again:
   ```bash
   clojure -T:build clean
   clojure -T:build jar
   ```

2. Check for uncommitted changes:
   ```bash
   git status
   ```

### Version Already Exists on Clojars

Clojars doesn't allow overwriting existing versions. You must:
1. Increment the version number in `build.clj`
2. Create a new tag
3. Deploy the new version

### GitHub Actions Fails

1. Check the Actions tab for error details
2. Verify secrets are set correctly in repository settings
3. Make sure the tag matches the pattern `v*.*.*`

## First-Time Setup

For the first release to Clojars:

1. **Claim Group ID**
   - Clojars will verify you own the GitHub repository
   - The first deployment will claim `io.github.nandoolle`

2. **Verify Deployment**
   - Check Clojars shows the correct metadata
   - Verify README and license display correctly

## Release Checklist

Before creating a release:

- [ ] All tests pass (`clojure -M:test`)
- [ ] Version updated in `build.clj`
- [ ] CHANGELOG.md updated with release notes
- [ ] README.md reflects current features
- [ ] All changes committed and pushed
- [ ] Tag created with correct version
- [ ] (For automated) GitHub secrets are set
- [ ] (For manual) Environment variables are set

After creating a release:

- [ ] Verify on Clojars
- [ ] Test installation in a new project
- [ ] Check GitHub release is created
- [ ] Announce release (optional)

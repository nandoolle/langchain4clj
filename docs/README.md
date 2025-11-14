# 📚 Documentation Directory

Comprehensive documentation for langchain4clj development and usage.

## 🌐 Jekyll Documentation Site

This documentation is served as a GitHub Pages site using Jekyll.

### Local Development

1. **Install Ruby and Bundler** (if not already installed):
   ```bash
   # macOS (using Homebrew)
   brew install ruby
   gem install bundler
   ```

2. **Install dependencies**:
   ```bash
   cd docs
   bundle install
   ```

3. **Serve the site locally**:
   ```bash
   bundle exec jekyll serve
   ```
   
   The site will be available at `http://localhost:4000/langchain4clj/`

4. **Build the site** (without serving):
   ```bash
   bundle exec jekyll build
   ```

### Site Structure

- `_config.yml` - Jekyll configuration
- `_layouts/` - HTML templates
- `_includes/` - Reusable HTML components
- `assets/` - CSS, JavaScript, images
- `*.md` - Documentation pages

### Viewing Online

The documentation is automatically published to GitHub Pages when changes are merged to `main`:
- **Site URL**: https://nandoolle.github.io/langchain4clj/

## 📁 Directory Structure

```
docs/
├── development/   # For developers and contributors
└── archive/       # Historical documentation
```

---

## 🔧 Development Documentation (`development/`)

Essential reading for anyone working on langchain4clj.

### Getting Started
- **[START_HERE.md](development/START_HERE.md)** - 🎯 Start here if you're new!
- **[CLAUDE.md](development/CLAUDE.md)** - Project context for AI assistants

### API & Migration
- **[API_IDIOMATIC_IMPROVEMENTS.md](development/API_IDIOMATIC_IMPROVEMENTS.md)** - Phase 1 API redesign
- **[MIGRATION_GUIDE.md](development/MIGRATION_GUIDE.md)** - v0.1.x → v0.2.0 migration
- **[API_MIGRATION_MAP.md](development/API_MIGRATION_MAP.md)** - API mapping reference

### Features & Proposals
- **[STREAMING_PROPOSAL.md](development/STREAMING_PROPOSAL.md)** - Streaming design rationale
- **[JSON_MODE_IMPLEMENTATION.md](development/JSON_MODE_IMPLEMENTATION.md)** - JSON mode details
- **[PROVIDERS.md](development/PROVIDERS.md)** - LLM provider reference

### Testing & Upgrades
- **[TESTING_GUIDE.md](development/TESTING_GUIDE.md)** - Testing infrastructure guide
- **[UPGRADE_1.8.0_PLAN.md](development/UPGRADE_1.8.0_PLAN.md)** - LangChain4j 1.8.0 upgrade plan

---

## 📦 Archive (`archive/`)

Historical documents preserved for reference.

### Test Reports
- `TEST_REPORT.md` - Original test analysis
- `TEST_FIXES_APPLIED.md` - Historical test fixes
- `TEST_FIXES_SUMMARY.md` - Test fix summaries

### Migration History
- `MIGRATION_SUMMARY.md` - API migration summary
- `MIGRATION_COMPLETE.md` - Migration completion report
- `FIXES_APPLIED.md` - Bug fixes applied
- `PRODUCTION_ERRORS.md` - Production error log

### Other
- `2025-11-09-analize-este-projeto-o-readme-o-roadmap-o-claud.txt` - Early analysis

---

## 🎯 Recommended Reading Order

### For New Contributors
1. [development/START_HERE.md](development/START_HERE.md)
2. [development/TESTING_GUIDE.md](development/TESTING_GUIDE.md)
3. [development/API_IDIOMATIC_IMPROVEMENTS.md](development/API_IDIOMATIC_IMPROVEMENTS.md)

### For Users Upgrading
1. [development/MIGRATION_GUIDE.md](development/MIGRATION_GUIDE.md)
2. [development/API_MIGRATION_MAP.md](development/API_MIGRATION_MAP.md)

### For Adding Features
1. [development/CLAUDE.md](development/CLAUDE.md) - Project philosophy
2. [development/PROVIDERS.md](development/PROVIDERS.md) - Adding providers
3. [development/STREAMING_PROPOSAL.md](development/STREAMING_PROPOSAL.md) - Design patterns

---

## 🔗 Main Project Documentation

- **[../README.md](../README.md)** - Main project README (user-facing)
- **[../CHANGELOG.md](../CHANGELOG.md)** - Complete change history
- **[../ROADMAP.md](../ROADMAP.md)** - Project roadmap and status

---

## 📝 Documentation Standards

When adding new documentation:

1. **File Naming**: Use SCREAMING_SNAKE_CASE.md for specs/guides
2. **Headers**: Use emoji + title for better navigation
3. **Structure**: Include table of contents for docs >200 lines
4. **Examples**: Always include practical code examples
5. **Links**: Use relative links within the repository
6. **Date**: Add "Last Updated" footer

---

## 🤝 Contributing to Docs

Found a typo? Have a clarification? Documentation contributions are welcome!

1. Edit the relevant file
2. Ensure links still work
3. Update "Last Updated" date
4. Submit a PR

---

**Last Updated**: February 2025

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

## 🔗 Main Project Documentation

- **[../README.md](../README.md)** - Main project README (user-facing)
- **[../CHANGELOG.md](../CHANGELOG.md)** - Complete change history

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

**Last Updated**: December 2025

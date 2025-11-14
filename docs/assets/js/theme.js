/**
 * Dark/Light Theme Toggle
 * Persists theme preference in localStorage
 */

(function() {
    'use strict';
    
    const THEME_KEY = 'langchain4clj-theme';
    const DARK_THEME = 'dark';
    const LIGHT_THEME = 'light';
    
    // Get elements
    const themeToggle = document.getElementById('theme-toggle');
    const themeIcon = document.querySelector('.theme-toggle-icon');
    
    /**
     * Get the current theme from localStorage or system preference
     */
    function getCurrentTheme() {
        const savedTheme = localStorage.getItem(THEME_KEY);
        
        if (savedTheme) {
            return savedTheme;
        }
        
        // Check system preference
        if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
            return DARK_THEME;
        }
        
        return LIGHT_THEME;
    }
    
    /**
     * Apply theme to the document
     */
    function applyTheme(theme) {
        if (theme === DARK_THEME) {
            document.documentElement.setAttribute('data-theme', 'dark');
            themeIcon.textContent = '☀️';
            themeToggle.setAttribute('aria-label', 'Switch to light mode');
        } else {
            document.documentElement.removeAttribute('data-theme');
            themeIcon.textContent = '🌙';
            themeToggle.setAttribute('aria-label', 'Switch to dark mode');
        }
    }
    
    /**
     * Toggle between dark and light themes
     */
    function toggleTheme() {
        const currentTheme = getCurrentTheme();
        const newTheme = currentTheme === DARK_THEME ? LIGHT_THEME : DARK_THEME;
        
        localStorage.setItem(THEME_KEY, newTheme);
        applyTheme(newTheme);
    }
    
    // Initialize theme on page load
    const initialTheme = getCurrentTheme();
    applyTheme(initialTheme);
    
    // Add event listener to toggle button
    if (themeToggle) {
        themeToggle.addEventListener('click', toggleTheme);
    }
    
    // Listen for system theme changes
    if (window.matchMedia) {
        window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', function(e) {
            // Only auto-switch if user hasn't manually set a preference
            if (!localStorage.getItem(THEME_KEY)) {
                applyTheme(e.matches ? DARK_THEME : LIGHT_THEME);
            }
        });
    }
})();

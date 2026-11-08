import js from '@eslint/js';
import reactHooks from 'eslint-plugin-react-hooks';
import globals from 'globals';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  { ignores: ['dist', 'node_modules', 'node_modules.nosync', 'e2e/node_modules', 'test-results', 'playwright-report'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    files: ['**/*.{ts,tsx}'],
    languageOptions: { globals: globals.browser },
    plugins: { 'react-hooks': reactHooks },
    rules: {
      ...reactHooks.configs.recommended.rules,
      // Dinheiro no cliente é string ou Decimal. Estas regras barram o float binário no lint.
      'no-restricted-globals': ['error', 'parseFloat'],
      'no-restricted-syntax': [
        'error',
        {
          selector: "CallExpression[callee.property.name='toFixed']",
          message: 'Use lib/format: toFixed opera sobre float binário.',
        },
        {
          selector: "CallExpression[callee.object.name='Number'][callee.property.name='parseFloat']",
          message: 'Use decimal.js para valores monetários.',
        },
      ],
    },
  },
);

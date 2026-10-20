// @ts-check
const eslint = require('@eslint/js');
const { defineConfig } = require('eslint/config');
const tseslint = require('typescript-eslint');
const angular = require('angular-eslint');

module.exports = defineConfig([
  {
    files: ['**/*.ts'],
    extends: [
      eslint.configs.recommended,
      tseslint.configs.recommended,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    rules: {
      // ADR 0010: bedragen blijven strings; nooit omzetten naar een (onnauwkeurige) number.
      'no-restricted-globals': [
        'error',
        { name: 'parseFloat', message: 'Bedragen blijven strings (ADR 0010).' },
        { name: 'parseInt', message: 'Bedragen blijven strings (ADR 0010).' },
      ],
      'no-restricted-syntax': [
        'error',
        { selector: "CallExpression[callee.name='Number']", message: 'Bedragen blijven strings (ADR 0010).' },
        { selector: "MemberExpression[object.name='Number'][property.name=/^parse/]", message: 'Bedragen blijven strings (ADR 0010).' },
        { selector: "UnaryExpression[operator='+'][argument.type!='Literal']", message: 'Bedragen blijven strings (ADR 0010).' },
      ],
      '@angular-eslint/directive-selector': [
        'error',
        {
          type: 'attribute',
          prefix: 'app',
          style: 'camelCase',
        },
      ],
      '@angular-eslint/component-selector': [
        'error',
        {
          type: 'element',
          prefix: 'app',
          style: 'kebab-case',
        },
      ],
    },
  },
  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {},
  },
]);

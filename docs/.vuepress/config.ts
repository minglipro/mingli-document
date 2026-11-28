import { defineUserConfig } from 'vuepress'
import { viteBundler } from '@vuepress/bundler-vite'
import { defaultTheme } from '@vuepress/theme-default'
import { searchPlugin } from '@vuepress/plugin-search'

export default defineUserConfig({
  lang: 'zh-CN',
  title: 'mingli-document',
  description: 'Kotlin/JVM 多格式文档映射库：JSON/YAML/XML/Properties/TOML/CSV/Ion/CBOR/Smile/Avro/Protobuf',
  base: '/mingli-document/',
  head: [
    ['meta', { name: 'theme-color', content: '#3eaf7c' }],
    ['meta', { name: 'description', content: 'mingli-document 官方文档' }],
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/mingli-document/favicon.svg' }],
    ['link', { rel: 'apple-touch-icon', href: '/mingli-document/logo-1024.png' }],
  ],
  bundler: viteBundler({
    viteOptions: {
      build: { cssMinify: false },
    },
  }),
  theme: defaultTheme({
    logo: '/logo.svg',
    logoDark: '/logo-dark.svg',
    repo: 'minglipro/mingli-document',
    docsDir: 'docs',
    editLink: true,
    lastUpdated: true,
    contributors: false,
    navbar: [
      { text: '首页', link: '/' },
      { text: '指南', link: '/guide/getting-started.html' },
      { text: '格式', link: '/formats/' },
      { text: '模块', link: '/modules/' },
      { text: '参考', link: '/reference/' },
      { text: '版本', children: ['/reference/changelog.html', '/reference/roadmap.html'] },
    ],
    sidebar: {
      '/guide/': [
        {
          text: '开始',
          children: [
            '/guide/getting-started.md',
            '/guide/installation.md',
            '/guide/core-concepts.md',
          ],
        },
        {
          text: '使用',
          children: [
            '/guide/serialization.md',
            '/guide/io.md',
            '/guide/streaming.md',
            '/guide/configuration.md',
            '/guide/token-stream.md',
          ],
        },
        {
          text: '高级',
          children: [
            '/guide/codegen.md',
            '/guide/fuzz-security.md',
            '/guide/jpms.md',
            '/guide/graalvm.md',
            '/guide/migration.md',
            '/guide/faq.md',
          ],
        },
      ],
      '/formats/': [
        {
          text: '文本格式',
          children: [
            '/formats/json.md',
            '/formats/yaml.md',
            '/formats/xml.md',
            '/formats/properties.md',
            '/formats/toml.md',
            '/formats/csv.md',
            '/formats/ion.md',
          ],
        },
        {
          text: '二进制格式',
          children: [
            '/formats/cbor.md',
            '/formats/smile.md',
            '/formats/avro.md',
            '/formats/protobuf.md',
          ],
        },
      ],
      '/modules/': [
        {
          text: '基础模块',
          children: [
            '/modules/annotation.md',
            '/modules/core.md',
            '/modules/exception.md',
            '/modules/schema.md',
            '/modules/schema-registry.md',
            '/modules/validation.md',
            '/modules/jdbc.md',
            '/modules/jpa.md',
            '/modules/jpa-hibernate.md',
          ],
        },
        {
          text: '映射模块',
          children: [
            '/modules/serialization.md',
            '/modules/serialization-kotlin.md',
          ],
        },
        {
          text: '工具模块',
          children: [
            '/modules/codegen.md',
            '/modules/openapi.md',
            '/modules/bom.md',
          ],
        },
      ],
      '/reference/': [
        {
          text: 'API 参考',
          children: [
            '/reference/annotations.md',
            '/reference/api.md',
            '/reference/exceptions.md',
            '/reference/token.md',
          ],
        },
        {
          text: '项目',
          children: [
            '/reference/changelog.md',
            '/reference/roadmap.md',
          ],
        },
      ],
    },
    sidebarDepth: 3,
    navbarDropdown: [],
  }),
  plugins: [
    searchPlugin({
      locales: {
        '/': { placeholder: '搜索文档' },
      },
      isSearchable: (page) => page.path !== '/',
      maxSuggestions: 10,
    }),
  ],
})

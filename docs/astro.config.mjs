import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

export default defineConfig({
  integrations: [
    starlight({
      title: 'PerfumIA',
      description: 'Documentacion del TFG DAW PerfumIA',
      head: [
        {
          tag: 'style',
          content: `
            .mermaid {
              margin: 1.5rem 0;
              overflow-x: auto;
              padding: 1rem;
              border: 1px solid var(--sl-color-gray-5);
              border-radius: 0.5rem;
              background: var(--sl-color-bg-nav);
              box-shadow: 0 16px 40px rgba(15, 23, 42, 0.08);
            }

            .mermaid svg {
              max-width: 100%;
              height: auto;
              display: block;
              margin: 0 auto;
            }

            .mermaid-error {
              margin: 1.5rem 0;
              padding: 1rem;
              border: 1px solid #ef4444;
              border-radius: 0.5rem;
              background: rgba(239, 68, 68, 0.08);
              color: var(--sl-color-text);
              white-space: pre-wrap;
            }
          `
        },
        {
          tag: 'script',
          attrs: { type: 'module' },
          content: `
            import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';

            mermaid.initialize({
              startOnLoad: false,
              securityLevel: 'loose',
              theme: 'base',
              themeVariables: {
                primaryColor: '#f7eee2',
                primaryTextColor: '#2f261f',
                primaryBorderColor: '#c08a3d',
                lineColor: '#9a6a2e',
                secondaryColor: '#fffaf3',
                tertiaryColor: '#f4d6a5',
                fontFamily: 'Inter, ui-sans-serif, system-ui, sans-serif'
              }
            });

            function getMermaidSource(pre) {
              const copied = pre
                .closest('figure')
                ?.querySelector('button[data-code]')
                ?.getAttribute('data-code');

              if (copied) {
                return copied.replace(/\\u007f/g, '\\n').trim();
              }

              const expressiveLines = Array.from(pre.querySelectorAll('.ec-line .code'));
              if (expressiveLines.length > 0) {
                return expressiveLines
                  .map((line) => line.textContent || '')
                  .join('\\n')
                  .trim();
              }

              return (pre.textContent || '').trim();
            }

            async function renderMermaidBlocks() {
              const blocks = Array.from(
                document.querySelectorAll('pre[data-language="mermaid"], pre > code.language-mermaid')
              );
              let index = 0;

              for (const block of blocks) {
                const pre = block.matches('pre') ? block : block.closest('pre');
                if (!pre) continue;

                const host = pre.closest('.expressive-code') || pre.closest('figure') || pre;
                if (host.dataset.mermaidRendered === 'true') continue;

                const source = getMermaidSource(pre);
                const container = document.createElement('div');
                container.className = 'mermaid';
                host.dataset.mermaidRendered = 'true';

                try {
                  const id = 'mermaid-' + index + '-' + Math.random().toString(36).slice(2);
                  const result = await mermaid.render(id, source);
                  container.innerHTML = result.svg;
                  host.replaceWith(container);
                } catch (error) {
                  host.dataset.mermaidRendered = 'false';
                  const fallback = document.createElement('pre');
                  fallback.className = 'mermaid-error';
                  fallback.textContent = 'No se pudo renderizar este diagrama Mermaid:\\n\\n' + source;
                  host.replaceWith(fallback);
                  console.warn('No se pudo renderizar un diagrama Mermaid', error);
                }

                index += 1;
              }
            }

            document.addEventListener('DOMContentLoaded', renderMermaidBlocks);
            document.addEventListener('astro:page-load', renderMermaidBlocks);
          `
        }
      ],
      sidebar: [
        { label: 'Inicio', slug: '' },
        { label: 'Introduccion', slug: 'introduccion' },
        { label: 'Tecnologias', slug: 'tecnologias' },
        { label: 'Instalacion', slug: 'instalacion' },
        { label: 'Guia de uso', slug: 'guia-uso' },
        { label: 'Cumplimiento DAW', slug: 'cumplimiento-daw' },
        { label: 'Arquitectura', slug: 'arquitectura' },
        { label: 'Base de datos', slug: 'base-datos' },
        {
          label: 'Diagramas',
          items: [
            { label: 'Casos de uso', slug: 'diagramas/casos-uso' },
            { label: 'Clases', slug: 'diagramas/clases' },
            { label: 'Entidad-Relacion', slug: 'diagramas/entidad-relacion' },
            { label: 'Componentes', slug: 'diagramas/componentes' },
            { label: 'Actividades', slug: 'diagramas/actividades' },
            { label: 'Secuencia', slug: 'diagramas/secuencia' },
            { label: 'Despliegue', slug: 'diagramas/despliegue' }
          ]
        },
        { label: 'Casos de prueba', slug: 'casos-prueba' },
        { label: 'Despliegue', slug: 'despliegue' },
        { label: 'Conclusion', slug: 'conclusion' }
      ]
    })
  ]
});

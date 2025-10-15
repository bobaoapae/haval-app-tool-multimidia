var fs = require('fs');
var path = require('path');

// Função para processar um HTML e inlinear CSS/JS
function processHtml(htmlPath, outputPath) {
  console.log(`🔄 Processando: ${htmlPath}`);
  
  if (!fs.existsSync(htmlPath)) {
    console.log(`❌ Arquivo não encontrado: ${htmlPath}`);
    return;
  }

  var htmlContent = fs.readFileSync(htmlPath, 'utf8');

  // Inline CSS
  var cssRegex = /<link[^>]*href=([^>\s]+\.css)[^>]*>/g;
  var cssMatch;
  while ((cssMatch = cssRegex.exec(htmlContent)) !== null) {
    var cssPath = cssMatch[1].replace(/['"]/g, '');
    
    // Converte caminho relativo para absoluto
    var fullCssPath;
    if (cssPath.startsWith('/src/')) {
      fullCssPath = path.join(__dirname, cssPath.substring(1));
    } else {
      fullCssPath = path.join(__dirname, 'dist', cssPath);
    }
    
    if (fs.existsSync(fullCssPath)) {
      var cssContent = fs.readFileSync(fullCssPath, 'utf8');
      htmlContent = htmlContent.replace(cssMatch[0], '<style>' + cssContent + '</style>');
      console.log('✅ CSS inlined:', cssPath);
    }
  }

  // Inline JavaScript
  var jsRegex = /<script[^>]*src=([^>\s]+\.js)[^>]*><\/script>/g;
  var jsMatch;
  while ((jsMatch = jsRegex.exec(htmlContent)) !== null) {
    var jsPath = jsMatch[1].replace(/['"]/g, '');
    
    var fullJsPath = path.join(__dirname, 'dist', jsPath);
    
    if (fs.existsSync(fullJsPath)) {
      var jsContent = fs.readFileSync(fullJsPath, 'utf8');
      htmlContent = htmlContent.replace(jsMatch[0], '<script>' + jsContent + '</script>');
      fs.unlinkSync(fullJsPath);
      console.log('✅ JS inlined:', jsPath);
    }
  }

  // Salva o HTML processado
  fs.writeFileSync(outputPath, htmlContent, 'utf8');
  console.log(`✅ HTML gerado: ${outputPath}`);
}

// Processa os dois temas
console.log('🚀 Iniciando build dos temas...');

// Night theme
var nightHtmlPath = path.join(__dirname, 'dist', 'app-night.html');
var nightOutputPath = path.join(__dirname, 'dist', 'app-night.html');
processHtml(nightHtmlPath, nightOutputPath);

// Light theme  
var lightHtmlPath = path.join(__dirname, 'dist', 'app-light.html');
var lightOutputPath = path.join(__dirname, 'dist', 'app-light.html');
processHtml(lightHtmlPath, lightOutputPath);

// Remove pasta assets vazia
var assetsDir = path.join(__dirname, 'dist', 'assets');
if (fs.existsSync(assetsDir)) {
  var files = fs.readdirSync(assetsDir);
  if (files.length === 0) {
    fs.rmdirSync(assetsDir);
    console.log('✅ Pasta assets removida');
  }
}

// Remove arquivos CSS originais
var cssFiles = ['night.style.css', 'light.style.css'];
cssFiles.forEach(function(cssFile) {
  var cssPath = path.join(__dirname, 'dist', cssFile);
  if (fs.existsSync(cssPath)) {
    fs.unlinkSync(cssPath);
    console.log(`✅ CSS removido: ${cssFile}`);
  }
});

console.log('🎉 Build completo! Arquivos gerados:');
console.log('  📄 app-night.html (tema escuro)');
console.log('  📄 app-light.html (tema claro)');

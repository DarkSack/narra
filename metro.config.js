// Learn more https://docs.expo.dev/guides/customizing-metro
const { getDefaultConfig } = require('expo/metro-config');

const config = getDefaultConfig(__dirname);

// Bundle the local pdf.js files (HTML shell + library + worker) as raw assets
// so they can be copied to a writable directory and loaded by a WebView.
config.resolver.assetExts.push('html', 'pdfjslib');

module.exports = config;

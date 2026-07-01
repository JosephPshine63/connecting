#!/usr/bin/env node
const fs = require('fs');
const path = require('path');

const bannerPath = path.join(__dirname, 'banner.txt');
const banner = fs.readFileSync(bannerPath, 'utf8');

console.log('\x1b[36m%s\x1b[0m', banner);
console.log('  :: WacChat frontend ::\n');

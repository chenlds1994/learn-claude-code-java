const fs = require('fs');
const c = fs.readFileSync('learn-claude-code-tutorial.drawio', 'utf-8');
const lines = c.split('\n');
const found = [];

function isAttrEnd(line, pos) {
    const rest = line.substring(pos + 1).trimStart();
    return rest.startsWith('style=') || rest.startsWith('vertex=') || 
           rest.startsWith('edge=') || rest.startsWith('parent=') ||
           rest.startsWith('source=') || rest.startsWith('target=');
}

for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (!line.includes('value=')) continue;
    let start = 0;
    while (true) {
        const vs = line.indexOf('value="', start);
        if (vs === -1) break;
        
        // Find the REAL end of value attribute
        let ve = -1;
        for (let j = vs + 7; j < line.length; j++) {
            if (line[j] === '"') {
                const prev = line.substring(Math.max(0, j - 5), j + 1);
                const prev2 = line.substring(Math.max(0, j - 4), j + 1);
                if (prev === '&quot;' || prev2 === '&#34;') continue;
                
                // Check if this is the real attribute end
                if (isAttrEnd(line, j)) {
                    ve = j;
                    break;
                }
                // If not, this is a nested unescaped quote
                // We still record ve to check the full value
                if (ve === -1) ve = j; // Remember first quote position for reporting
            }
        }
        
        if (ve === -1) { start = vs + 1; continue; }
        
        // Now re-find the actual attribute end by checking isAttrEnd
        let actualEnd = -1;
        for (let j = vs + 7; j < line.length; j++) {
            if (line[j] === '"') {
                const prev = line.substring(Math.max(0, j - 5), j + 1);
                const prev2 = line.substring(Math.max(0, j - 4), j + 1);
                if (prev === '&quot;' || prev2 === '&#34;') continue;
                if (isAttrEnd(line, j)) {
                    actualEnd = j;
                    break;
                }
            }
        }
        
        if (actualEnd === -1) actualEnd = ve;
        
        const val = line.substring(vs + 7, actualEnd);
        let hasUnescaped = false;
        for (let k = 0; k < val.length; k++) {
            if (val[k] === '"') {
                if (k >= 5 && val.substring(k - 5, k + 1) === '&quot;') continue;
                if (k >= 4 && val.substring(k - 4, k + 1) === '&#34;') continue;
                hasUnescaped = true;
                break;
            }
        }
        if (hasUnescaped) {
            found.push({ line: i + 1, text: line.trim() });
        }
        start = actualEnd + 1;
    }
}

let out = 'Found ' + found.length + ' bad lines\n';
for (const f of found) {
    out += 'Line ' + f.line + ': ' + f.text.substring(0, 150) + '\n';
}
fs.writeFileSync('bad-lines.txt', out, 'utf-8');

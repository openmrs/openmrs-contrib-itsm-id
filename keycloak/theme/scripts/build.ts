import { ZipArchive } from 'archiver';
import { createWriteStream, existsSync, mkdirSync } from 'node:fs';

import pkg from '../package.json' with { type: 'json' };

const dir = 'out';
const file = `${pkg.name}.jar`;
const path = `${dir}/${file}`;

!existsSync(dir) && mkdirSync(dir);

const output = createWriteStream(`${import.meta.dirname}/../${path}`);

const archive = new ZipArchive({ zlib: { level: 9 } });

archive.on('error', (error: unknown) => {
  throw error;
});

archive.pipe(output);

archive.directory('META-INF', 'META-INF');
archive.directory('theme', 'theme');

archive.finalize();

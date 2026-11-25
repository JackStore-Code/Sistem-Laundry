"""
cari_teks_gui.py - Cari lokasi teks yang tampil di GUI.

Pakai:
    python alat/cari_teks_gui.py              -> cetak tabel semua teks GUI
    python alat/cari_teks_gui.py "Kata"       -> cari teks yang mengandung "Kata"
    python alat/cari_teks_gui.py --simpan     -> simpan ke alat/daftar_teks_gui.txt

Hanya membaca berkas, tidak mengubah apa pun.
"""

import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Pola baris yang teksnya kelihatan di layar
POLA_UI = re.compile(
    r'setText\s*\(|new JLabel\s*\(|new Tombol\s*\(|setTitle\s*\(|JOptionPane|'
    r'new JCheckBox\s*\(|new JRadioButton\s*\(|new JTextField\s*\(|'
    r'new JButton\s*\(|setToolTipText\s*\(|KOLOM\s*=|showMessageDialog|'
    r'setPlaceholder|Teks\s*=|String\[\]\s*KOLOM'
)

LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')

# Baris yang isinya cuma kunci SQL / nama kolom database -> biasanya bukan teks GUI
POLA_SKIP = re.compile(r'SELECT |INSERT |UPDATE |DELETE |CREATE |ALTER |PRAGMA ')


def berkas_java():
    hasil = []
    for akar, _, nama in os.walk(ROOT):
        if any(x in akar for x in ('.git', '.build', '.smoke', '.uji_db', 'alat')):
            continue
        for n in nama:
            if n.endswith('.java'):
                hasil.append(os.path.join(akar, n))
    return sorted(hasil)


def kumpulkan():
    baris_hasil = []
    for path in berkas_java():
        rel = os.path.relpath(path, ROOT).replace('\\', '/')
        with io.open(path, encoding='utf-8', errors='replace') as f:
            for no, baris in enumerate(f, 1):
                if not POLA_UI.search(baris):
                    continue
                if POLA_SKIP.search(baris):
                    continue
                for lit in LITERAL.findall(baris):
                    if not lit.strip():
                        continue
                    # lewati yang jelas bukan teks tampilan
                    if lit.startswith(('java.', 'com.', 'org.', 'http', '%s', '.db', 'UTF')):
                        continue
                    if len(lit) > 200:
                        continue
                    baris_hasil.append((rel, no, lit, baris.strip()))
    return baris_hasil


def main():
    args = [a for a in sys.argv[1:]]
    simpan = '--simpan' in args
    args = [a for a in args if a != '--simpan']
    kata = args[0].lower() if args else None

    data = kumpulkan()
    if kata:
        data = [d for d in data if kata in d[2].lower()]

    keluaran = []
    for rel, no, lit, sumber in data:
        keluaran.append('%s:%d\n    "%s"' % (rel, no, lit))

    teks = '\n'.join(keluaran)
    print('Ditemukan %d teks GUI\n' % len(data))
    print(teks)

    if simpan:
        tujuan = os.path.join(ROOT, 'alat', 'daftar_teks_gui.txt')
        with io.open(tujuan, 'w', encoding='utf-8') as f:
            f.write(teks + '\n')
        print('\nDisimpan ke: %s' % tujuan)


if __name__ == '__main__':
    main()

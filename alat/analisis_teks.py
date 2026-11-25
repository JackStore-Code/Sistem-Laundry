"""
analisis_teks.py - Temukan teks yang HARUS diganti serentak.

Menganalisis PER-LITERAL (bukan per-baris), jadi akurat:
literal "Berat (kg)" tidak dianggap logika hanya karena di baris yang sama
ada "Kiloan".equals(...).

Pakai:
    python alat/analisis_teks.py                    -> daftar lengkap
    python alat/analisis_teks.py --detail "Selesai" -> lihat semua kemunculan
"""

import io
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SUMBER = os.path.join(ROOT, 'LaundryApp.java')

LITERAL = re.compile(r'"((?:[^"\\]|\\.)*)"')

# Literal yang merupakan perintah database, bukan teks GUI
SQL_AWAL = re.compile(r'^\s*(SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|PRAGMA|DROP)\b')

# Nama kolom / kunci internal -> bukan teks GUI
KUNCI_INTERNAL = {'status', 'jenis', 'item', 'nama', 'telepon', 'jumlah', 'tarif',
                  'total', 'tanggal', 'estimasi_hari', 'tanggal_selesai', 'id',
                  'batal', 'ENTER', 'ESCAPE', '--cek', '--check', 'utf-8', 'UTF-8',
                  # teknis, bukan teks GUI
                  '-', '.jar', 'sqlite', 'Rp ', '\\"ok\\":true', 'ok\\":false',
                  'tidak valid', 'tidak ada balasan', 'Content-Type',
                  'application/json', 'config.properties', 'webhook.wa',
                  'evolution.apikey', 'jdbc:sqlite:', ';', ', ', '\\",'}


def esc(lit):
    return re.escape('"' + lit + '"')


def peran(lit, isi):
    """Tentukan peran literal INI (bukan seluruh baris) dalam satu baris kode."""
    e = esc(lit)
    p = set()

    # --- dipakai sebagai NILAI yang dibandingkan ---
    if re.search(r'\.equals\s*\(\s*' + e, isi) or \
       re.search(r'\.equalsIgnoreCase\s*\(\s*' + e, isi) or \
       re.search(e + r'\s*\.equals', isi):
        p.add('dibanding .equals()')
    if re.search(r'(==|!=)\s*' + e, isi) or re.search(e + r'\s*(==|!=)', isi):
        p.add('dibanding ==')
    if re.search(r'\bcase\s+' + e, isi):
        p.add('switch/case')

    # --- disimpan / dikembalikan sebagai nilai ---
    if re.search(r'(setStatus|setJenis|setPilihan|setItem|setMode|setTipe|setAktif)\s*\(\s*' + e, isi):
        p.add('disimpan lewat setter')
    if re.search(r'new\s+String\[\]\s*\{[^}]*' + e, isi):
        p.add('anggota daftar nilai')
    if re.search(r'^\s*(private|public|protected|static|final|\s)*[\w<>\[\]]+\s+\w+\s*=\s*' + e + r'\s*;', isi):
        p.add('nilai awal field')
    if re.search(r'\breturn\s+' + e, isi):
        p.add('nilai kembalian')
    if re.search(r'\bput\s*\(\s*' + e, isi):
        p.add('kunci map')
    if re.search(r'\.replace\s*\(\s*' + e, isi) or re.search(r'\.split\s*\(\s*' + e, isi) or \
       re.search(r'\.startsWith\s*\(\s*' + e, isi) or re.search(r'\.endsWith\s*\(\s*' + e, isi) or \
       re.search(r'\.contains\s*\(\s*' + e, isi):
        p.add('dipotong/dicocokkan .replace/split/contains')

    # --- teks yang tampil di layar ---
    if re.search(r'setText\s*\(\s*' + e, isi):
        p.add('tampil: setText')
    if re.search(r'new\s+(JLabel|Tombol|JButton|Kolom|Pilih|Segmen|Lencana|'
                 r'KartuStatistik|JCheckBox|JRadioButton|JTextField)\s*\(\s*' + e, isi):
        p.add('tampil: komponen')
    if re.search(r'setTitle\s*\(\s*' + e, isi):
        p.add('tampil: judul jendela')
    if re.search(r'setToolTipText\s*\(\s*' + e, isi):
        p.add('tampil: tooltip')
    if re.search(r'(showMessageDialog|showInputDialog|showConfirmDialog)\s*\([^;]*' + e, isi):
        p.add('tampil: dialog')

    return p


PERAN_NILAI = {'dibanding .equals()', 'dibanding ==', 'switch/case',
               'disimpan lewat setter', 'anggota daftar nilai',
               'nilai awal field', 'nilai kembalian', 'kunci map',
               'dipotong/dicocokkan .replace/split/contains'}

PERAN_TAMPIL = {'tampil: setText', 'tampil: komponen', 'tampil: judul jendela',
                'tampil: tooltip', 'tampil: dialog'}


def main():
    arg = sys.argv[1:]
    with io.open(SUMBER, encoding='utf-8', errors='replace') as f:
        baris = f.readlines()

    data = defaultdict(list)   # literal -> [(no, isi, peran)]
    for no, isi in enumerate(baris, 1):
        for lit in LITERAL.findall(isi):
            if not lit.strip():
                continue
            data[lit].append((no, isi.strip(), peran(lit, isi)))

    if arg and arg[0] == '--detail' and len(arg) > 1:
        t = arg[1]
        if t not in data:
            print('Literal "%s" tidak ditemukan.' % t)
            return
        print('=' * 76)
        print('DETAIL "%s" -> %d kemunculan' % (t, len(data[t])))
        print('=' * 76)
        for no, isi, p in data[t]:
            print('\n  baris %d' % no)
            print('     peran : %s' % (', '.join(sorted(p)) or '(tak terklasifikasi)'))
            print('     kode  : %s' % isi[:150])
        return

    hasil = []
    for lit, kem in data.items():
        if SQL_AWAL.match(lit):
            continue
        if lit in KUNCI_INTERNAL:
            continue
        gabung = set()
        for _, _, p in kem:
            gabung |= p
        tanda_nilai = gabung & PERAN_NILAI
        if not tanda_nilai:
            continue
        hasil.append((lit, kem, gabung, tanda_nilai))

    hasil.sort(key=lambda x: (-len({n for n, _, _ in x[1]}), x[0]))

    print('=' * 76)
    print('TEKS YANG HARUS DIGANTI SERENTAK (dipakai sebagai nilai logika/data)')
    print('=' * 76)

    for lit, kem, gabung, tanda in hasil:
        nomor = sorted({n for n, _, _ in kem})
        tampil = bool(gabung & PERAN_TAMPIL)
        jenis = 'NILAI + TAMPILAN' if tampil else 'NILAI saja (tidak tampil)'
        print('\n"%s"   [%s]' % (lit, jenis))
        print('   %d baris: %s' % (len(nomor), ', '.join(str(n) for n in nomor)))
        print('   peran: %s' % ', '.join(sorted(tanda)))

    print('\n' + '=' * 76)
    print('%d teks perlu diganti serentak.' % len(hasil))
    print('=' * 76)


if __name__ == '__main__':
    main()

"""
cek_risiko.py - Sebelum ganti teks, cek apakah teks itu "aman" atau berbahaya.

Pakai:
    python alat/cek_risiko.py "Kiloan"
    python alat/cek_risiko.py "Bening Laundry"

Menjawab 2 pertanyaan:
  1. Teks ini dipakai di berapa tempat?
  2. Apakah dipakai untuk LOGIKA / DATABASE (bukan cuma tampilan)?
"""

import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Tanda bahwa teks dipakai sebagai data/logika, bukan sekadar tampilan
TANDA_LOGIKA = [
    (re.compile(r'\.equals\s*\('), 'dibandingkan dengan .equals() -> dipakai logika'),
    (re.compile(r'\.equalsIgnoreCase\s*\('), 'dibandingkan dengan .equalsIgnoreCase()'),
    (re.compile(r'SELECT|INSERT|UPDATE|DELETE'), 'muncul di perintah SQL -> tersimpan di database'),
    (re.compile(r'switch|case '), 'dipakai di switch/case'),
    (re.compile(r'getStatus|setStatus|getJenis'), 'dipakai sebagai nilai status/jenis'),
]


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    kata = sys.argv[1]

    path = os.path.join(ROOT, 'LaundryApp.java')
    with io.open(path, encoding='utf-8', errors='replace') as f:
        baris = f.readlines()

    pola = re.compile(r'"((?:[^"\\]|\\.)*)"')
    temuan = []
    for no, isi in enumerate(baris, 1):
        if ('"%s"' % kata) in isi:
            temuan.append((no, isi.rstrip()))

    if not temuan:
        print('Tidak ketemu teks "%s" di LaundryApp.java' % kata)
        return

    print('=' * 60)
    print('Teks dicari : "%s"' % kata)
    print('Ditemukan di: %d baris' % len(temuan))
    print('=' * 60)

    bahaya = 0
    for no, isi in temuan:
        alasan = []
        for pola2, ket in TANDA_LOGIKA:
            if pola2.search(isi):
                alasan.append(ket)
        tanda = '  !! BERISIKO' if alasan else '  -- aman (tampilan)'
        print('\nBaris %d%s' % (no, tanda))
        print('    %s' % isi.strip()[:150])
        for a in alasan:
            print('    * %s' % a)
        if alasan:
            bahaya += 1

    print('\n' + '=' * 60)
    if bahaya:
        print('KESIMPULAN: JANGAN ganti sembarangan.')
        print('%d dari %d tempat memakai teks ini untuk logika/database.' % (bahaya, len(temuan)))
        print('Kalau diganti, harus diganti SERENTAK di semua baris di atas')
        print('+ data lama di database (db_laundry.db) ikut di-update.')
    else:
        print('KESIMPULAN: Aman diganti. Ini murni teks tampilan.')
    print('=' * 60)


if __name__ == '__main__':
    main()

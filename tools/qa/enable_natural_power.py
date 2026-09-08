#!/usr/bin/env python3
"""Extend only the QA starter class; never patch any production implementation.

The standalone acquisition run remains independently reproducible. This opt-in test
composition adds actual fuel-paid wire production. CI archives the exact applied diff.
"""
import hashlib
from pathlib import Path

path = Path('src/main/kotlin/mods/eln/devtest/NaturalStarterChecks.kt')
original = path.read_bytes()
blob = hashlib.sha1(b'blob ' + str(len(original)).encode() + b'\0' + original).hexdigest()
if blob != '52fc7fc54ea324a70f0368c450a8f4326c9fb1c9':
    raise SystemExit('Unexpected QA starter revision; inspect rather than patching an unknown file')
text = original.decode('utf-8')
old = '                report.write(true);finished=true;event.server.halt(false)'
new = '''                craft("eln:iron_roller_wheel",2)
                NaturalPowerInstallation.start(world,player,table!!,::remove,::placeOn,::receive)
                stage=4'''
assert text.count(old) == 1
text = text.replace(old, new)
old = '            if(tick>48000) error('
new = '''            if(tick%20==0 && stage==4 && NaturalPowerInstallation.step()) {
                checkStep("fuel-paid-native-roller-and-insulator") {
                    record("natural-powered-wire-production-complete",mapOf("qualification" to "Actual thermal/electrical processing from gathered fuel; no creative source or battery attached"))
                }
                report.write(true);finished=true;event.server.halt(false)
            }
            if(tick>48000) error('''
assert text.count(old) == 1
text = text.replace(old, new)
path.write_text(text, encoding='utf-8')
print('Applied known QA-only power extension; production simulator is unchanged')

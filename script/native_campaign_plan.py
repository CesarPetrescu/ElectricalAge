"""Independent expected-case catalogue; changes are reviewed alongside new fixtures."""
SUITES = ('power', 'logic', 'mechanical', 'storage-thermal')

def expected(suite):
    if suite == 'power':
        return ([f'converter-{i}-{state}' for i in range(7) for state in ('loaded', 'open')]
            + ['converter-native-voltage-entry', 'source-native-voltage-entry']
            + [f'parallel-four-{s}' for s in ('rated','overload','recovery','missing-input','unequal-input')]
            + [f'parallel-seed-20260909-{i}' for i in range(8)])
    if suite == 'logic':
        return ([f'logic-{name}-truth-{i}' for name, n in [('not',2),('and',8),('nand',8),('or',8),('nor',8),('xor',8),('xnor',8),('pal',8)] for i in range(n)]
            + [f'logic-schmitttrigger-{s}' for s in ('low','high','hold','reset')]
            + [f'logic-dflipflop-{s}' for s in ('clear','data','edge','hold','fall','clear-edge')]
            + [f'logic-jkflipflop-{s}' for s in ('idle','set','fall','toggle')]
            + ['logic-oscillator-pulses'] + [f'logic-chain-{i}' for i in range(3)])
    if suite == 'mechanical':
        return ['shaft-loaded','shaft-coasting','shaft-split','shaft-reconnect','large-shaft-loaded','large-shaft-remove']
    if suite == 'storage-thermal':
        batteries=('cost_oriented_battery','capacity_oriented_battery','voltage_oriented_battery','current_oriented_battery','life_oriented_battery','single-use_battery','experimental_battery')
        return [f'battery-{name}-{s}' for name in batteries for s in ('discharge','open')] + ['fuel-thermal-electric','thermal-break','thermal-reconnect']
    raise ValueError(f'Unknown suite {suite}')

def gallery_id(entry):
    return f"gallery-{entry['id'].replace(':','-')}-{entry['descriptor']}-{entry['side']}"

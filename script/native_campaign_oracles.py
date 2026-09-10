"""Independent evidence checks for the defects that previously produced misleading passes."""
from __future__ import annotations
import math


def finite(value):
    if type(value) not in (int, float) or not math.isfinite(value):
        raise ValueError(f'Nonfinite/non-numeric observation: {value!r}')
    return value


def near(actual, target, tolerance, label):
    if abs(finite(actual) - finite(target)) > tolerance:
        raise ValueError(f'{label}: {actual} differs from {target} by more than {tolerance}')


def validate_observation(suite, case, observed):
    if suite == 'logic' and case.startswith('logic-'):
        near(observed['signalSupplyV'], 5.0, 1e-9, 'Production signal domain')
        requested, actual = observed['requestedInputsV'], observed['observedInputsV']
        if not requested or len(requested) != len(actual):
            raise ValueError('Missing logic inputs')
        for index, (target, volts) in enumerate(zip(requested, actual)):
            if not 0 <= finite(target) <= 5:
                raise ValueError('Logic input outside 0..5 V')
            near(volts, target, .1, f'Logic input {index}')
        if '-truth-' in case:
            gate, bits = case.removeprefix('logic-').split('-truth-')
            count = 1 if gate == 'not' else 3
            expected_inputs = [5.0 if int(bits) & (1 << i) else 0.0 for i in range(count)]
            if requested != expected_inputs:
                raise ValueError('Requested inputs do not match the named truth-table row')
        if 'expectedHigh' in observed:
            if type(observed['expectedHigh']) is not bool:
                raise ValueError('Missing digital expected state')
            near(observed['outputV'], 5.0 if observed['expectedHigh'] else 0.0, .25, 'Logic output')
        if case == 'logic-oscillator-pulses' and finite(observed['transitions']) < 1:
            raise ValueError('No oscillator transition')
    if suite == 'storage-thermal' and case.startswith('battery-'):
        internal = finite(observed['internalCurrentA'])
        external = finite(observed['externalCurrentA'])
        total = finite(observed['totalCurrentA'])
        resistance = finite(observed['selfDischargeOhms'])
        if resistance <= 0 or observed['electricalSamples'] <= 0:
            raise ValueError('No physical battery samples or internal resistor')
        near(internal, finite(observed['voltageV']) / resistance, 1e-7 + abs(internal)*1e-5, 'Battery self discharge')
        near(total, external + internal, 1e-5 + abs(total)*1e-4, 'Battery KCL')
        if case.endswith('-open'):
            near(external, 0.0, 1e-6, 'Open external battery load')
            if observed['externalOpen'] is not True:
                raise ValueError('External load not removed')
        elif external <= 0:
            raise ValueError('Declared load draws no current')
    if case == 'shaft-unsafe-reinsert':
        first, second = finite(observed['motorBeforeRadS']), finite(observed['generatorBeforeRadS'])
        if not any(speed > 50 - .1*speed for speed in (first, second)):
            raise ValueError('Unsafe placement lacks a speed mismatch')
        if (observed['placementObserved'] is not True or observed['replacementDestroyed'] is not True
                or observed['ghostConnection'] is not False or observed['sharedNetwork'] is not False):
            raise ValueError('Rigid mismatch hazard was not actually demonstrated')
    if case == 'shaft-safe-reinsert':
        if (observed['sharedNetwork'] is not True or observed['survivedSlowTicks'] is not True
                or not 0 <= finite(observed['motorBeforeRadS']) < 20
                or not 0 <= finite(observed['generatorBeforeRadS']) < 20):
            raise ValueError('Safe reinsertion lacks verified low-speed preconditions and joined topology')
    if case == 'clutch-synchronised':
        if observed['distinctNetworks'] is not True or observed['slipping'] is not False:
            raise ValueError('Clutch must synchronise without merging its separate networks')
        near(observed['deltaRadS'], 0.0, .1, 'Locked clutch speed')
    if case == 'clutch-coal-destroyed':
        if (finite(observed['engagementDeltaRadS']) <= 5 or observed['clutchPresent'] is not False
                or observed['expectedDestruction'] is not True or observed['distinctNetworks'] is not True):
            raise ValueError('Explosive clutch hazard not demonstrated')

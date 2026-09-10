"""Fast checks of production-profile and independent observed-value evidence contracts."""
import copy
import unittest
from build_native_campaign_seed import validate_receipt, validate_smoke_log
from native_campaign_oracles import validate_observation
from native_campaign_plan import expected


class NativeCorrectnessTest(unittest.TestCase):
    def setUp(self):
        self.receipt = dict(schema=1, production=True, jarSha256='a'*64, registryVerified=True,
                            pid=42, registry=['eln:dc-dc_converter#128'])
        self.logic = dict(signalSupplyV=5.0, requestedInputsV=[5., 0., 0.], observedInputsV=[4.999, 0., 0.],
                          outputV=0., expectedHigh=False)
        self.battery = dict(voltageV=121.394, selfDischargeOhms=288., internalCurrentA=121.394/288.,
                            externalCurrentA=0., totalCurrentA=121.394/288., electricalSamples=50,
                            externalOpen=True)
        self.hazard = dict(motorBeforeRadS=187.6, generatorBeforeRadS=30., placementObserved=True,
                           replacementDestroyed=True, ghostConnection=False, sharedNetwork=False)

    def test_production_seed_with_exact_artifact(self): validate_receipt(self.receipt, 'a'*64)
    def test_development_seed_rejected(self):
        self.receipt['production'] = False
        with self.assertRaises(ValueError): validate_receipt(self.receipt, 'a'*64)
    def test_wrong_seed_jar_rejected(self):
        with self.assertRaises(ValueError): validate_receipt(self.receipt, 'b'*64)
    def test_unverified_registry_rejected(self):
        self.receipt['registryVerified'] = False
        with self.assertRaises(ValueError): validate_receipt(self.receipt, 'a'*64)
    def test_packaged_smoke_must_complete(self):
        validate_smoke_log('SMOKE all checks passed, stopping server')
        with self.assertRaises(ValueError): validate_smoke_log('Started server')
        with self.assertRaises(ValueError): validate_smoke_log('SMOKE all checks passed, stopping server\nSMOKE 1 check(s) FAILED, stopping server')
    def test_empty_seed_registry_rejected(self):
        self.receipt['registry'] = []
        with self.assertRaises(ValueError): validate_receipt(self.receipt, 'a'*64)
    def test_development_item_rejected(self):
        self.receipt['registry'].append('eln:isolation_transformer#135')
        with self.assertRaises(ValueError): validate_receipt(self.receipt, 'a'*64)
    def test_failed_power_case_not_in_production_plan(self):
        self.assertNotIn('converter-6-loaded', expected('power'))
        self.assertEqual(sum(c.startswith('converter-') and c.endswith('-loaded') for c in expected('power')), 6)
    def test_correct_logic_input_and_low_output(self): validate_observation('logic', 'logic-and-truth-1', self.logic)
    def test_zero_inputs_cannot_fake_a_low_output_pass(self):
        self.logic['observedInputsV'] = [0., 0., 0.]
        with self.assertRaises(ValueError): validate_observation('logic', 'logic-and-truth-1', self.logic)
    def test_named_truth_row_cannot_be_relabelled(self):
        self.logic.update(requestedInputsV=[0.,0.,0.], observedInputsV=[0.,0.,0.])
        with self.assertRaises(ValueError): validate_observation('logic', 'logic-and-truth-1', self.logic)
    def test_fifty_volt_domain_rejected(self):
        self.logic.update(signalSupplyV=50.,requestedInputsV=[50.,0.,0.],observedInputsV=[50.,0.,0.])
        with self.assertRaises(ValueError): validate_observation('logic', 'logic-and-truth-1', self.logic)
    def test_nan_logic_observation_rejected(self):
        self.logic['observedInputsV'][0] = float('nan')
        with self.assertRaises(ValueError): validate_observation('logic', 'logic-and-truth-1', self.logic)
    def test_self_discharge_is_not_phantom_load(self):
        self.assertGreater(self.battery['totalCurrentA'], .1)
        validate_observation('storage-thermal', 'battery-experimental_battery-open', self.battery)
    def test_external_open_current_is_checked(self):
        self.battery['externalCurrentA'] = 1.
        self.battery['totalCurrentA'] += 1.
        with self.assertRaises(ValueError): validate_observation('storage-thermal', 'battery-experimental_battery-open', self.battery)
    def test_unaccounted_cell_current_rejected(self):
        self.battery['totalCurrentA'] += .1
        with self.assertRaises(ValueError): validate_observation('storage-thermal', 'battery-experimental_battery-open', self.battery)
    def test_actual_unsafe_hazard_accepted(self): validate_observation('mechanical', 'shaft-unsafe-reinsert', self.hazard)
    def test_hazard_requires_actual_placement(self):
        self.hazard['placementObserved'] = False
        with self.assertRaises(ValueError): validate_observation('mechanical', 'shaft-unsafe-reinsert', self.hazard)
    def test_hazard_requires_speed_precondition(self):
        self.hazard.update(motorBeforeRadS=0.,generatorBeforeRadS=0.)
        with self.assertRaises(ValueError): validate_observation('mechanical', 'shaft-unsafe-reinsert', self.hazard)
    def test_safe_reinsert_requires_low_speed_and_survival(self):
        result=dict(motorBeforeRadS=12.,generatorBeforeRadS=4.,sharedNetwork=True,survivedSlowTicks=True)
        validate_observation('mechanical', 'shaft-safe-reinsert', result)
        result['motorBeforeRadS'] = 187.
        with self.assertRaises(ValueError): validate_observation('mechanical', 'shaft-safe-reinsert', result)
    def test_clutch_lock_must_not_merge_networks(self):
        result=dict(distinctNetworks=True,slipping=False,deltaRadS=.001)
        validate_observation('mechanical', 'clutch-synchronised', result)
        result['distinctNetworks'] = False
        with self.assertRaises(ValueError): validate_observation('mechanical', 'clutch-synchronised', result)
    def test_coal_plate_hazard_requires_speed_and_destruction(self):
        result=dict(engagementDeltaRadS=100.,clutchPresent=False,expectedDestruction=True,distinctNetworks=True)
        validate_observation('mechanical', 'clutch-coal-destroyed', result)
        result['engagementDeltaRadS'] = 1.
        with self.assertRaises(ValueError): validate_observation('mechanical', 'clutch-coal-destroyed', result)


if __name__ == '__main__': unittest.main(verbosity=2)

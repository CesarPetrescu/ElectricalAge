import unittest
from rewrite_members import rewrite,resource_factories

class MemberMigrationTests(unittest.TestCase):
    def test_nbt_java(self):
        self.assertIn('nbt.putDouble(',rewrite('void f(CompoundTag nbt) { nbt.setDouble("k", 3); }'))
    def test_nbt_kotlin(self):
        self.assertIn('nbt.putInt(',rewrite('fun f(nbt: CompoundTag) { nbt.setInteger("x", 3) }'))
    def test_unrelated_owners(self):
        out=rewrite('void f(CompoundTag nbt) { nbt.hasKey("x"); I18n.hasKey("x"); }')
        self.assertIn('nbt.contains(',out);self.assertIn('I18n.hasKey(',out)
    def test_payload_keys_unchanged(self):
        self.assertIn('"nbt.setDouble"',rewrite('void f(CompoundTag nbt) { nbt.setString("nbt.setDouble", "getStackInSlot"); }'))
    def test_single_constructor(self):
        self.assertEqual(resource_factories('new ResourceLocation("eln:foo")'),'ResourceLocation.parse("eln:foo")')
    def test_two_constructor(self):
        self.assertEqual(resource_factories('ResourceLocation("eln", name())'),'ResourceLocation.fromNamespaceAndPath("eln", name())')
    def test_nested_commas(self):
        self.assertEqual(resource_factories('new ResourceLocation(f(a, b))'),'ResourceLocation.parse(f(a, b))')
    def test_strings_no_constructor(self):
        self.assertEqual(resource_factories('"new ResourceLocation(a, b)"'),'"new ResourceLocation(a, b)"')
    def test_idempotent(self):
        s='fun f(nbt: CompoundTag) { nbt.setInteger("x", 3); world.isRemote }'
        self.assertEqual(rewrite(rewrite(s)),rewrite(s))

if __name__=='__main__':unittest.main()

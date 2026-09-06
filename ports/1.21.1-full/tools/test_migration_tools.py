import unittest
from rewrite_types import rewrite
from compile_report import parse

class MigrationTests(unittest.TestCase):
    def test_rename(self):
        m={'old.EntityPlayer':'modern.Player'}
        self.assertEqual(rewrite('import old.EntityPlayer;\nEntityPlayer p;\n',m),'import modern.Player;\nPlayer p;\n')
    def test_comments_strings_untouched(self):
        m={'old.EntityPlayer':'modern.Player'}
        text='import old.EntityPlayer;\n// EntityPlayer old.EntityPlayer\nString s="EntityPlayer"; EntityPlayer p;'
        self.assertIn('// EntityPlayer old.EntityPlayer',rewrite(text,m))
        self.assertIn('"EntityPlayer"; Player p;',rewrite(text,m))
    def test_simultaneous(self):
        m={'old.IContainer':'modern.Container','old.Container':'modern.Menu'}
        self.assertIn('Container x; Menu y;',rewrite('import old.IContainer;\nimport old.Container;\nIContainer x; Container y;',m))
    def test_collision_java(self):
        m={'old.Facing':'modern.Direction'}
        t='import old.Facing;\nimport local.Direction;\nFacing a; Direction b;'
        self.assertIn('modern.Direction a; Direction b;',rewrite(t,m))
    def test_collision_kotlin(self):
        m={'old.Facing':'modern.Direction'}
        t='import old.Facing\nimport local.Direction\nval a: Facing'
        self.assertIn('import modern.Direction as Facing',rewrite(t,m,True))
        self.assertIn('val a: Facing',rewrite(t,m,True))
    def test_idempotent(self):
        m={'old.Facing':'modern.Direction'}
        t='import old.Facing;\nFacing a;'
        once=rewrite(t,m)
        self.assertEqual(rewrite(once,m),once)
    def test_fully_qualified(self):
        self.assertEqual(rewrite('old.Player.MP p;',{'old.Player':'newer.Player'}),'newer.Player.MP p;')
    def test_kotlin_diagnostic(self):
        d=parse("e: file:///work/src/main/java/A.kt:4:6 Unresolved reference 'World'.")
        self.assertEqual(len(d),1)
        self.assertEqual(d[0]['file'],'src/main/java/A.kt')
    def test_java_diagnostic(self):
        self.assertEqual(len(parse('/work/src/main/java/A.java:2: error: cannot find symbol')),1)
    def test_setup_error_is_not_a_source_error(self):
        self.assertEqual(parse('Could not resolve services.gradle.org'),[])

if __name__=='__main__': unittest.main()

#!/usr/bin/env perl
# Mechanical 1.7.10 -> 1.12.2 API renames. Idempotent: safe to re-run.
#
#   perl tools/port/rewrite.pl $(git ls-files 'src/main/**.java' 'src/main/**.kt')
#
# Only rules that are true unconditionally live here. Anything that needs a look
# at the surrounding code (BlockPos signatures, ItemStack.EMPTY, IIcon, GL state)
# is deliberately left alone so the compiler still reports it.
use strict;
use warnings;

# --- package moves from the 1.8 "util" reorganisation -----------------------
my @imports = (
    ['net\.minecraft\.util\.AxisAlignedBB'              => 'net.minecraft.util.math.AxisAlignedBB'],
    ['net\.minecraft\.util\.Vec3\b'                     => 'net.minecraft.util.math.Vec3d'],
    ['net\.minecraft\.util\.MovingObjectPosition'       => 'net.minecraft.util.math.RayTraceResult'],
    ['net\.minecraft\.util\.MathHelper'                 => 'net.minecraft.util.math.MathHelper'],
    ['net\.minecraft\.util\.ChunkCoordinates'           => 'net.minecraft.util.math.BlockPos'],
    ['net\.minecraft\.util\.EnumChatFormatting'         => 'net.minecraft.util.text.TextFormatting'],
    ['net\.minecraft\.util\.ChatComponentText'          => 'net.minecraft.util.text.TextComponentString'],
    ['net\.minecraft\.util\.IChatComponent'             => 'net.minecraft.util.text.ITextComponent'],
    ['net\.minecraft\.util\.StatCollector'              => 'net.minecraft.util.text.translation.I18n'],
    ['net\.minecraft\.world\.ChunkCoordIntPair'         => 'net.minecraft.util.math.ChunkPos'],
    ['net\.minecraft\.world\.biome\.BiomeGenBase'       => 'net.minecraft.world.biome.Biome'],
    ['net\.minecraft\.network\.play\.server\.S35PacketUpdateTileEntity'
                                                        => 'net.minecraft.network.play.server.SPacketUpdateTileEntity'],
    ['net\.minecraftforge\.common\.util\.ForgeDirection' => 'net.minecraft.util.EnumFacing'],
    ['cpw\.mods\.fml\.'                                 => 'net.minecraftforge.fml.'],
);

# --- identifier renames (whole word) ---------------------------------------
my @idents = (
    ['Vec3'                     => 'Vec3d'],
    ['MovingObjectPosition'     => 'RayTraceResult'],
    ['ForgeDirection'           => 'EnumFacing'],
    ['EnumChatFormatting'       => 'TextFormatting'],
    ['ChatComponentText'        => 'TextComponentString'],
    ['IChatComponent'           => 'ITextComponent'],
    ['StatCollector'            => 'I18n'],
    ['ChunkCoordIntPair'        => 'ChunkPos'],
    ['BiomeGenBase'             => 'Biome'],
    ['S35PacketUpdateTileEntity' => 'SPacketUpdateTileEntity'],
);

# --- member renames (fields and methods; may be dot-qualified) --------------
my @members = (
    ['worldObj'                 => 'world'],
    ['theWorld'                 => 'world'],
    ['thePlayer'                => 'player'],
    ['addChatMessage'           => 'sendMessage'],
    ['getBiomeGenForCoords'     => 'getBiome'],
    ['fontRendererObj'          => 'fontRenderer'],
    ['mcProfiler'               => 'profiler'],
    ['currentEquippedItem'      => 'heldItemMainhand'],
    ['getCurrentEquippedItem'   => 'getHeldItemMainhand'],
    ['spawnEntityInWorld'       => 'spawnEntity'],
    ['blockExists'              => 'isBlockLoaded'],
    # stable_39 for 1.12.2 uses the modern names; verified against
    # build/rfg/minecraft-src (Block.setTranslationKey, ResourceLocation.getPath).
    ['setBlockName'             => 'setTranslationKey'],
    ['setUnlocalizedName'       => 'setTranslationKey'],
    ['getUnlocalizedName'       => 'getTranslationKey'],
    ['unlocalizedName'          => 'translationKey'],
    ['func_150939_a'            => 'block'],
    ['hasNoTags'                => 'isEmpty'],
);

# --- expression rewrites ----------------------------------------------------
my @exprs = (
    # WorldProvider.dimensionId became a private field with a getDimension() accessor (1.9).
    # Only the qualified form is rewritten: bare "dimensionId" is also a common local name.
    [qr/\bprovider\.dimensionId\b/                         => 'provider.dimension'],
    [qr/\bprovider!!\.dimensionId\b/                       => 'provider!!.dimension'],
    [qr/\bprovider\?\.dimensionId\b/                      => 'provider?.dimension'],
    [qr/\bMathHelper\.floor_(double|float)\s*\(/     => 'MathHelper.floor('],
    [qr/\bMathHelper\.ceiling_(double|float)_int\s*\(/ => 'MathHelper.ceil('],
    [qr/\bEnumFacing\.VALID_DIRECTIONS\b/           => 'EnumFacing.VALUES'],
    [qr/\bS3FPacketCustomPayload\b/                  => 'SPacketCustomPayload'],
    [qr/net\.minecraft\.network\.play\.server\.SPacketCustomPayload/ => 'net.minecraft.network.play.server.SPacketCustomPayload'],
    [qr/\bVec3d\.createVectorHelper\s*\(/            => 'Vec3d('],
    [qr/\bItemStack\.loadItemStackFromNBT\s*\(/       => 'ItemStack('],
    [qr/\bVec3\.createVectorHelper\s*\(/             => 'Vec3d('],
    [qr/\bAxisAlignedBB\.getBoundingBox\s*\(/        => 'AxisAlignedBB('],
    [qr/\bTessellator\.instance\b/                   => 'Tessellator.getInstance()'],
    [qr/\bMinecraftServer\.getServer\s*\(\s*\)/      => 'FMLCommonHandler.instance().getMinecraftServerInstance()'],
    [qr/\bFMLCommonHandler\.instance\(\)\.bus\(\)/   => 'MinecraftForge.EVENT_BUS'],
);

# The compatibility bridges deliberately name the old API in their documentation,
# so rewriting them is never correct.
my %skip = map { $_ => 1 } (
    'src/main/kotlin/mods/eln/misc/McBridge.kt',
    'src/main/kotlin/mods/eln/client/itemrender/LegacyItemRender.kt',
    # Forge's Fluid kept setUnlocalizedName; the MCP rename does not apply to it.
    'src/main/kotlin/mods/eln/fluid/FluidRegistration.kt',
);

my $changed_files = 0;
for my $file (@ARGV) {
    next if $skip{$file};
    open my $fh, '<', $file or die "$file: $!";
    my $src = do { local $/; <$fh> };
    close $fh;
    my $orig = $src;

    for my $r (@imports) { my ($from, $to) = @$r; $src =~ s/$from/$to/g; }
    for my $r (@idents)  {
        my ($from, $to) = @$r;
        $src =~ s/(?<![\w.\$])\Q$from\E\b/$to/g;
    }
    for my $r (@members) {
        my ($from, $to) = @$r;
        $src =~ s/(?<![\w\$])\Q$from\E\b/$to/g;
    }
    for my $r (@exprs)   { my ($from, $to) = @$r; $src =~ s/$from/$to/g; }

    # Blocks.foo_bar / Items.foo_bar became SCREAMING_SNAKE constants in 1.11.
    $src =~ s/\b(Blocks|Items)\.([a-z][a-z0-9_]*)\b/"$1." . uc($2)/ge;
    # Material.packedIce / Material.iron became Material.PACKED_ICE / Material.IRON in 1.9.
    $src =~ s/\bMaterial\.([a-z][a-zA-Z0-9]*)\b(?!\s*\()/"Material." . uc($1 =~ s|([a-z])([A-Z])|$1_$2|gr)/ge;

    # net.minecraft.util.math.Vec3d.createVectorHelper style leftovers
    $src =~ s/\bVec3dd\b/Vec3d/g;

    if ($src ne $orig) {
        open my $out, '>', $file or die "$file: $!";
        print $out $src;
        close $out;
        $changed_files++;
    }
}
print "rewrote $changed_files of " . scalar(@ARGV) . " files\n";

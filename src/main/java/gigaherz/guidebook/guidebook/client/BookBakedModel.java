package gigaherz.guidebook.guidebook.client;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import gigaherz.guidebook.guidebook.BookDocument;
import gigaherz.guidebook.guidebook.BookRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.renderer.model.*;
import net.minecraft.client.renderer.texture.ISprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.Direction;
import net.minecraft.util.JSONUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.model.IModelConfiguration;
import net.minecraftforge.client.model.IModelLoader;
import net.minecraftforge.client.model.geometry.IModelGeometry;
import net.minecraftforge.resource.IResourceType;
import net.minecraftforge.resource.SelectiveReloadStateHandler;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class BookBakedModel implements IBakedModel
{
    private final ItemCameraTransforms cameraTransforms;
    private final TextureAtlasSprite particle;
    private final ItemOverrideList overrideList;

    public BookBakedModel(IBakedModel baseModel, ModelBakery bakery, IUnbakedModel unbakedModel, Function<ResourceLocation, IUnbakedModel> modelGetter,
                          Function<ResourceLocation, TextureAtlasSprite> spriteGetter, ItemCameraTransforms cameraTransforms,
                          Map<ResourceLocation, IBakedModel> bookModels, Map<ResourceLocation, IBakedModel> coverModels, @Nullable TextureAtlasSprite particle, VertexFormat format)
    {
        this.particle = particle;
        this.cameraTransforms = cameraTransforms;
        this.overrideList = new ItemOverrideList(bakery, unbakedModel, modelGetter, spriteGetter, Collections.emptyList(), format)
        {
            @Nullable
            @Override
            public IBakedModel getModelWithOverrides(IBakedModel model, ItemStack stack, @Nullable World worldIn, @Nullable LivingEntity entityIn)
            {
                CompoundNBT tag = stack.getTag();
                if (tag != null)
                {
                    String book = tag.getString("Book");
                    BookDocument bookDocument = BookRegistry.get(new ResourceLocation(book));
                    if (bookDocument != null)
                    {
                        ResourceLocation modelLocation = bookDocument.getModel();
                        if (modelLocation != null)
                        {
                            return bookModels.get(modelLocation);
                        }
                        else
                        {
                            ResourceLocation cover = bookDocument.getCover();

                            if (cover != null)
                                return coverModels.get(cover);
                        }
                    }
                }

                return baseModel;
            }
        };
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, Random rand)
    {
        return Collections.emptyList();
    }

    @Override
    public boolean isAmbientOcclusion()
    {
        return true;
    }

    @Override
    public boolean isGui3d()
    {
        return true;
    }

    @Override
    public boolean isBuiltInRenderer()
    {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleTexture()
    {
        return particle;
    }

    @Deprecated
    @Override
    public ItemCameraTransforms getItemCameraTransforms()
    {
        return cameraTransforms;
    }

    @Override
    public ItemOverrideList getOverrides()
    {
        return overrideList;
    }

    public static class Model implements IModelGeometry<Model>
    {
        private final BlockModel baseModel;
        private final Map<ResourceLocation, IUnbakedModel> bookModels = Maps.newHashMap();
        private final Map<ResourceLocation, IUnbakedModel> coverModels = Maps.newHashMap();

        public Model(BlockModel baseModel)
        {
            this.baseModel = baseModel;
        }

        @Override
        public IBakedModel bake(IModelConfiguration owner, ModelBakery bakery, Function<ResourceLocation, TextureAtlasSprite> spriteGetter, ISprite sprite, VertexFormat format, ItemOverrideList overrides)
        {
            String particleLocation = owner.resolveTexture("particle");
            TextureAtlasSprite part = spriteGetter.apply(new ResourceLocation(particleLocation));

            Map<ResourceLocation, IBakedModel> bakedBookModels = Maps.transformEntries(bookModels, (k, v) -> v.bake(bakery, spriteGetter, sprite, format));
            Map<ResourceLocation, IBakedModel> bakedCoverModels = Maps.transformEntries(coverModels, (k, v) -> v.bake(bakery, spriteGetter, sprite, format));

            return new BookBakedModel(
                    baseModel.bake(bakery, baseModel, spriteGetter, sprite, format),
                    bakery, owner.getOwnerModel(), bakery::getUnbakedModel, spriteGetter, owner.getCameraTransforms(), bakedBookModels, bakedCoverModels, part, format);
        }

        @Override
        public Collection<ResourceLocation> getTextureDependencies(IModelConfiguration owner, Function<ResourceLocation, IUnbakedModel> modelGetter, Set<String> missingTextureErrors)
        {
            Set<ResourceLocation> textures = Sets.newHashSet();

            textures.addAll(baseModel.getTextures(modelGetter, missingTextureErrors));

            for (ResourceLocation bookModel : BookRegistry.gatherBookModels())
            {
                bookModels.computeIfAbsent(bookModel, (loc) -> {
                    IUnbakedModel mdl = modelGetter.apply(loc);
                    textures.addAll(mdl.getTextures(modelGetter, missingTextureErrors));
                    return mdl;
                });
            }

            for (ResourceLocation bookCover : BookRegistry.gatherBookCovers())
            {
                coverModels.computeIfAbsent(bookCover, (loc) -> {
                    BlockModel mdl = new BlockModel(
                            new ResourceLocation(bookCover.getNamespace(), "generated/cover_models/" + bookCover.getPath()),
                            Collections.emptyList(),
                            ImmutableMap.of("cover", bookCover.toString()),
                            true, true, ItemCameraTransforms.DEFAULT, Collections.emptyList());
                    mdl.parent = baseModel;
                    textures.addAll(mdl.getTextures(modelGetter, missingTextureErrors));
                    return mdl;
                });
            }

            for (BookDocument renderer : BookRegistry.getLoadedBooks().values())
            {
                renderer.findTextures(textures);
            }

            return textures;
        }
    }

    public static class ModelLoader implements IModelLoader<Model>
    {
        @Override
        public void onResourceManagerReload(IResourceManager resourceManager)
        {
            // For compatibility, call the selective version from the non-selective function
            onResourceManagerReload(resourceManager, SelectiveReloadStateHandler.INSTANCE.get());
        }

        @Override
        public void onResourceManagerReload(IResourceManager resourceManager, Predicate<IResourceType> resourcePredicate)
        {
            if (resourcePredicate.test(BookResourceType.INSTANCE))
                BookRegistry.parseAllBooks(resourceManager);
        }

        @Override
        public Model read(JsonDeserializationContext deserializationContext, JsonObject modelContents)
        {
            BlockModel baseModel = deserializationContext.deserialize(JSONUtils.getJsonObject(modelContents, "base_model"), BlockModel.class);
            return new Model(baseModel);
        }
    }
}

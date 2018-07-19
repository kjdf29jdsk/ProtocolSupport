package protocolsupport.protocol.typeremapper.itemstack.toclient;

import protocolsupport.api.ProtocolVersion;
import protocolsupport.api.TranslationAPI;
import protocolsupport.protocol.typeremapper.itemstack.ItemStackSpecificRemapper;
import protocolsupport.zplatform.itemstack.ItemStackWrapper;

public class ButtonSpecificRemapper implements ItemStackSpecificRemapper {

	public ButtonSpecificRemapper() {

	}

	@Override
	public ItemStackWrapper remap(ProtocolVersion version, String locale, ItemStackWrapper itemstack) {
		itemstack.setDisplayName(TranslationAPI.translate(locale, "tile.button.name"));
		return itemstack;
	}

}

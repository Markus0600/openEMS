import { Component } from "@angular/core";
import { AbstractFlatWidget } from "src/app/shared/components/flat/abstract-flat-widget";
import { ChannelAddress, Utils, CurrentData } from "src/app/shared/shared";

@Component({
    selector: "SensataBMS",
    templateUrl: "./SensataBms.html",
    standalone: false,
})
export class SensataBmsComponent extends AbstractFlatWidget {


    protected override getChannelAddresses() {
        return [
            new ChannelAddress(this.component.id, "CAPACITY"),
            new ChannelAddress(this.component.id, "VOLTAGE"),
            new ChannelAddress(this.component.id, "CHARGE_MAX_CURRENT"),
            new ChannelAddress(this.component.id, "DISCHARGE_MAX_CURRENT"),
            new ChannelAddress(this.component.id, "SOC"),
            new ChannelAddress(this.component.id, "CURRENT"),
            new ChannelAddress(this.component.id, "MIN_CELL_VOLTAGE"),
            new ChannelAddress(this.component.id, "MAX_CELL_VOLTAGE"),
        ];
    }
}